package devops.iam.tenant;

import devops.iam.api.IamException;
import devops.iam.dao.ProjectRoleBindingDao;
import devops.iam.dao.TenantDao;
import devops.iam.event.IamEventPublisher;
import devops.iam.event.RoleChangedEvent;
import devops.iam.event.TenantMemberRemovedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 租户成员生命周期规则：所有租户内访问均以成员关系为前提。 */
@Service
public class TenantService {
    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final TenantDao dao;
    private final ProjectRoleBindingDao projectRoleBindingDao;
    private final IamEventPublisher eventPublisher;

    public TenantService(TenantDao dao, ProjectRoleBindingDao projectRoleBindingDao, IamEventPublisher eventPublisher) {
        this.dao = dao;
        this.projectRoleBindingDao = projectRoleBindingDao;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TenantView create(String userId, String name) {
        String normalizedName = validateTenantName(name);
        Instant now = Instant.now();
        String tenantId = UUID.randomUUID().toString();
        dao.createTenant(tenantId, normalizedName, userId, now);
        dao.createMember(UUID.randomUUID().toString(), tenantId, userId, Role.TENANT_ADMIN.name(), now);
        return new TenantView(tenantId, normalizedName, Role.TENANT_ADMIN.name(), now);
    }

    public List<TenantView> listMine(String userId) {
        return dao.findTenantsByUser(userId).stream()
                .map(row -> new TenantView(row.id(), row.name(), row.roleCode(), row.createdAt()))
                .toList();
    }

    public List<MemberView> listMembers(String userId, String tenantId) {
        requireMember(userId, tenantId);
        return dao.listMembers(tenantId).stream()
                .map(row -> new MemberView(row.id(), row.userId(), row.roleCode(), row.joinedAt()))
                .toList();
    }

    @Transactional
    public InvitationView invite(String inviterId, String tenantId, String login, String roleCode) {
        requireTenantAdmin(inviterId, tenantId);
        Role role = Role.parse(roleCode);
        TenantDao.UserRow invited = dao.findUserByLogin(login)
                .orElseThrow(() -> error("INVITED_USER_NOT_FOUND", HttpStatus.BAD_REQUEST, "受邀用户必须已注册"));
        if (dao.findMember(tenantId, invited.id()).isPresent()) {
            throw error("ALREADY_TENANT_MEMBER", HttpStatus.CONFLICT, "用户已经是租户成员");
        }
        Instant now = Instant.now();
        dao.deleteExpiredInvitations(now);
        if (dao.findPendingInvitation(tenantId, invited.id(), now).isPresent()) {
            throw error("INVITATION_ALREADY_PENDING", HttpStatus.CONFLICT, "该用户已有待处理邀请");
        }
        String invitationId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(INVITATION_TTL);
        dao.createInvitation(invitationId, tenantId, invited.id(), role.name(), inviterId, now, expiresAt);
        return new InvitationView(invitationId, tenantId, invited.id(), role.name(), "PENDING", expiresAt);
    }

    @Transactional
    public void acceptInvitation(String userId, String invitationId) {
        TenantDao.InvitationRow invitation = pendingInvitation(invitationId, userId);
        if (dao.findMember(invitation.tenantId(), userId).isPresent()) {
            throw error("ALREADY_TENANT_MEMBER", HttpStatus.CONFLICT, "用户已经是租户成员");
        }
        Instant now = Instant.now();
        dao.createMember(UUID.randomUUID().toString(), invitation.tenantId(), userId, invitation.roleCode(), now);
        dao.resolveInvitation(invitationId, "ACCEPTED", now);
    }

    public InvitationView getInvitation(String userId, String invitationId) {
        TenantDao.InvitationRow invitation = pendingInvitation(invitationId, userId);
        return new InvitationView(invitation.id(), invitation.tenantId(), invitation.invitedUserId(),
                invitation.roleCode(), invitation.status(), invitation.expiresAt());
    }

    @Transactional
    public void rejectInvitation(String userId, String invitationId) {
        dao.resolveInvitation(pendingInvitation(invitationId, userId).id(), "REJECTED", Instant.now());
    }

    @Transactional
    public void revokeInvitation(String actorId, String invitationId) {
        TenantDao.InvitationRow invitation = dao.findInvitation(invitationId)
                .orElseThrow(() -> hidden("邀请不存在或不可见"));
        requireTenantAdmin(actorId, invitation.tenantId());
        if (!"PENDING".equals(invitation.status())) {
            throw error("INVITATION_NOT_PENDING", HttpStatus.CONFLICT, "邀请已处理");
        }
        dao.resolveInvitation(invitationId, "REVOKED", Instant.now());
    }

    @Transactional
    public void updateRole(String actorId, String tenantId, String memberId, String roleCode) {
        requireTenantAdmin(actorId, tenantId);
        TenantDao.MemberRow member = member(tenantId, memberId);
        Role targetRole = Role.parse(roleCode);
        protectLastAdministrator(tenantId, member, targetRole);
        Instant now = Instant.now();
        dao.updateMemberRole(memberId, targetRole.name(), now);
        eventPublisher.publishAfterCommit(new RoleChangedEvent(tenantId, member.userId(), member.roleCode(),
                targetRole.name(), now));
    }

    @Transactional
    public void removeMember(String actorId, String tenantId, String memberId) {
        requireTenantAdmin(actorId, tenantId);
        TenantDao.MemberRow member = member(tenantId, memberId);
        protectLastAdministrator(tenantId, member, null);
        projectRoleBindingDao.deleteByMember(memberId);
        dao.deleteMember(memberId);
        eventPublisher.publishAfterCommit(new TenantMemberRemovedEvent(tenantId, member.userId(), Instant.now()));
    }

    @Transactional
    public void leave(String userId, String tenantId) {
        TenantDao.MemberRow member = requireMember(userId, tenantId);
        protectLastAdministrator(tenantId, member, null);
        projectRoleBindingDao.deleteByMember(member.id());
        dao.deleteMember(member.id());
        eventPublisher.publishAfterCommit(new TenantMemberRemovedEvent(tenantId, member.userId(), Instant.now()));
    }

    public TenantDao.MemberRow requireMember(String userId, String tenantId) {
        return dao.findMember(tenantId, userId).orElseThrow(() -> hidden("租户不存在或不可见"));
    }

    public TenantDao.MemberRow requireTenantAdmin(String userId, String tenantId) {
        TenantDao.MemberRow member = requireMember(userId, tenantId);
        if (Role.TENANT_ADMIN.name().equals(member.roleCode())) {
            return member;
        }
        throw error("ACCESS_DENIED", HttpStatus.FORBIDDEN, "没有执行此操作的权限");
    }

    /** 仅供 IAM 内部校验目标成员仍属于指定租户，业务模块不应调用此方法。 */
    public TenantDao.MemberRow findMemberInternal(String tenantId, String memberId) {
        return dao.findMemberById(tenantId, memberId).orElseThrow(() -> hidden("成员不存在或不可见"));
    }

    private TenantDao.InvitationRow pendingInvitation(String invitationId, String userId) {
        TenantDao.InvitationRow invitation = dao.findInvitation(invitationId)
                .orElseThrow(() -> hidden("邀请不存在或不可见"));
        if (!userId.equals(invitation.invitedUserId())) {
            throw hidden("邀请不存在或不可见");
        }
        if (invitation.expiresAt().isBefore(Instant.now()) || !"PENDING".equals(invitation.status())) {
            throw error("INVITATION_NOT_PENDING", HttpStatus.CONFLICT, "邀请已过期或已处理");
        }
        return invitation;
    }

    private String validateTenantName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 128) {
            throw error("TENANT_NAME_INVALID", HttpStatus.BAD_REQUEST, "租户名称长度应为 1 到 128 个字符");
        }
        return name.trim();
    }

    private void protectLastAdministrator(String tenantId, TenantDao.MemberRow member, Role targetRole) {
        boolean removesAdministrator = Role.TENANT_ADMIN.name().equals(member.roleCode())
                && (targetRole == null || targetRole != Role.TENANT_ADMIN);
        if (removesAdministrator && dao.countAdministrators(tenantId) <= 1) {
            throw error("LAST_TENANT_ADMIN", HttpStatus.CONFLICT, "租户至少需要保留一名管理员");
        }
    }

    private TenantDao.MemberRow member(String tenantId, String memberId) {
        return dao.findMemberById(tenantId, memberId).orElseThrow(() -> hidden("成员不存在或不可见"));
    }

    private IamException hidden(String message) {
        return error("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    private IamException error(String code, HttpStatus status, String message) {
        return new IamException(code, status, message);
    }

    /** 租户成员只有普通成员和租户管理员；项目角色由独立绑定记录维护。 */
    public enum Role {
        TENANT_ADMIN, MEMBER;

        public static Role parse(String value) {
            try {
                return Role.valueOf(value);
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IamException("ROLE_INVALID", HttpStatus.BAD_REQUEST, "租户角色不受支持");
            }
        }
    }

    public record TenantView(String id, String name, String roleCode, Instant createdAt) { }

    public record MemberView(String id, String userId, String roleCode, Instant joinedAt) { }

    public record InvitationView(String id, String tenantId, String invitedUserId, String roleCode,
                                 String status, Instant expiresAt) { }
}

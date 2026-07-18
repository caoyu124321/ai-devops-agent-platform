package devops.iam.dao;

import devops.iam.persistence.mapper.TenantMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 租户领域持久化门面，服务层不直接依赖 MyBatis 映射细节。 */
@Repository
public class TenantDao {
    private final TenantMapper mapper;

    public TenantDao(TenantMapper mapper) {
        this.mapper = mapper;
    }

    public void createTenant(String id, String name, String creatorId, Instant now) {
        mapper.createTenant(id, name, creatorId, now);
    }

    public void createMember(String id, String tenantId, String userId, String roleCode, Instant now) {
        mapper.createMember(id, tenantId, userId, roleCode, now);
    }

    public List<TenantRow> findTenantsByUser(String userId) {
        return mapper.findTenantsByUser(userId);
    }

    public Optional<MemberRow> findMember(String tenantId, String userId) {
        return Optional.ofNullable(mapper.findMember(tenantId, userId));
    }

    public Optional<MemberRow> findMemberById(String tenantId, String memberId) {
        return Optional.ofNullable(mapper.findMemberById(tenantId, memberId));
    }

    public List<MemberRow> listMembers(String tenantId) {
        return mapper.listMembers(tenantId);
    }

    public int countAdministrators(String tenantId) {
        return mapper.countAdministrators(tenantId);
    }

    public void updateMemberRole(String memberId, String roleCode, Instant now) {
        mapper.updateMemberRole(memberId, roleCode, now);
    }

    public void deleteMember(String memberId) {
        mapper.deleteMember(memberId);
    }

    public Optional<UserRow> findUserByLogin(String login) {
        return Optional.ofNullable(mapper.findUserByLogin(login));
    }

    public void deleteExpiredInvitations(Instant now) {
        mapper.deleteExpiredInvitations(now);
    }

    public Optional<InvitationRow> findPendingInvitation(String tenantId, String userId, Instant now) {
        return Optional.ofNullable(mapper.findPendingInvitation(tenantId, userId, now));
    }

    public void createInvitation(String id, String tenantId, String invitedUserId, String roleCode,
                                 String invitedBy, Instant now, Instant expiresAt) {
        mapper.createInvitation(id, tenantId, invitedUserId, roleCode, invitedBy, now, expiresAt);
    }

    public Optional<InvitationRow> findInvitation(String id) {
        return Optional.ofNullable(mapper.findInvitation(id));
    }

    public void resolveInvitation(String id, String status, Instant now) {
        mapper.resolveInvitation(id, status, now);
    }

    public record TenantRow(String id, String name, String createdBy, Instant createdAt, String roleCode) { }

    public record MemberRow(String id, String tenantId, String userId, String roleCode, Instant joinedAt) { }

    public record UserRow(String id, String username, String email) { }

    public record InvitationRow(String id, String tenantId, String invitedUserId, String roleCode, String invitedBy,
                                Instant createdAt, Instant expiresAt, String status) { }
}

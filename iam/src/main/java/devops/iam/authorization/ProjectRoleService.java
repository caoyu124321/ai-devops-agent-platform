package devops.iam.authorization;

import devops.iam.api.IamException;
import devops.iam.dao.ProjectRoleBindingDao;
import devops.iam.dao.TenantDao;
import devops.iam.event.IamEventPublisher;
import devops.iam.event.ProjectRoleChangedEvent;
import devops.iam.tenant.TenantService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目角色仅保存调用方提供的抽象项目标识，不依赖项目模块的数据或服务。 */
@Service
public class ProjectRoleService {
    private final ProjectRoleBindingDao bindingDao;
    private final TenantService tenantService;
    private final IamEventPublisher eventPublisher;

    public ProjectRoleService(ProjectRoleBindingDao bindingDao, TenantService tenantService,
                              IamEventPublisher eventPublisher) {
        this.bindingDao = bindingDao;
        this.tenantService = tenantService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void bind(String actorId, String tenantId, String memberId, String projectId, String roleCode) {
        TenantDao.MemberRow actor = tenantService.requireTenantAdmin(actorId, tenantId);
        TenantDao.MemberRow targetMember = tenantService.findMemberInternal(tenantId, memberId);
        validateProjectId(projectId);
        validateProjectRole(roleCode);
        Instant now = Instant.now();
        bindingDao.create(UUID.randomUUID().toString(), tenantId, targetMember.id(), projectId, roleCode,
                actor.userId(), now);
        eventPublisher.publishAfterCommit(new ProjectRoleChangedEvent(tenantId, targetMember.userId(), projectId,
                roleCode, now));
    }

    public List<ProjectRoleView> list(String actorId, String tenantId, String memberId) {
        tenantService.requireMember(actorId, tenantId);
        tenantService.findMemberInternal(tenantId, memberId);
        return bindingDao.listByMember(tenantId, memberId).stream()
                .map(row -> new ProjectRoleView(row.projectId(), row.roleCode()))
                .toList();
    }

    @Transactional
    public void unbind(String actorId, String tenantId, String memberId, String projectId) {
        tenantService.requireTenantAdmin(actorId, tenantId);
        TenantDao.MemberRow targetMember = tenantService.findMemberInternal(tenantId, memberId);
        validateProjectId(projectId);
        if (!bindingDao.deleteByMemberAndProject(tenantId, memberId, projectId)) {
            throw new IamException("PROJECT_ROLE_NOT_FOUND", HttpStatus.NOT_FOUND, "项目角色不存在或不可见");
        }
        eventPublisher.publishAfterCommit(new ProjectRoleChangedEvent(tenantId, targetMember.userId(), projectId,
                null, Instant.now()));
    }

    /** 项目删除前清理全部抽象项目角色，避免 IAM 保留已不存在项目的授权记录。 */
    @Transactional
    public void unbindProject(String actorId, String tenantId, String projectId) {
        tenantService.requireTenantAdmin(actorId, tenantId);
        validateProjectId(projectId);
        bindingDao.deleteByProject(tenantId, projectId);
    }

    private void validateProjectId(String projectId) {
        if (projectId == null || projectId.isBlank() || projectId.length() > 64) {
            throw new IamException("PROJECT_ID_INVALID", HttpStatus.BAD_REQUEST, "项目标识不合法");
        }
    }

    private void validateProjectRole(String roleCode) {
        if (!"PROJECT_ADMIN".equals(roleCode) && !"DEVELOPER".equals(roleCode)) {
            throw new IamException("PROJECT_ROLE_INVALID", HttpStatus.BAD_REQUEST, "项目角色不受支持");
        }
    }

    public record ProjectRoleView(String projectId, String roleCode) { }
}

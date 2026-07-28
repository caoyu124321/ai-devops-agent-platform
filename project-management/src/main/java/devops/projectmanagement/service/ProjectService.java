package devops.projectmanagement.service;

import devops.iam.authorization.ProjectRoleService;
import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationDecision;
import devops.iam.contract.AuthorizationScope;
import devops.iam.contract.AuthorizationService;
import devops.iam.tenant.TenantService;
import devops.projectmanagement.api.ProjectManagementException;
import devops.projectmanagement.dao.ProjectDao;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目服务在写入前统一鉴权，并将当前项目变化同步写入不可变版本记录。 */
@Service
public class ProjectService {
    private static final String PROJECT_RESOURCE = "PROJECT";
    private static final String ALL_RESOURCES = "*";
    private final ProjectDao projectDao;
    private final AuthorizationService authorizationService;
    private final TenantService tenantService;
    private final ProjectRoleService projectRoleService;

    public ProjectService(ProjectDao projectDao, AuthorizationService authorizationService, TenantService tenantService,
                          ProjectRoleService projectRoleService) {
        this.projectDao = projectDao;
        this.authorizationService = authorizationService;
        this.tenantService = tenantService;
        this.projectRoleService = projectRoleService;
    }

    @Transactional
    public ProjectView create(String actorId, String tenantId, String name, String description) {
        validateName(name);
        require(actorId, tenantId, null, ALL_RESOURCES, "project.create", AuthorizationScope.ScopeType.TENANT);
        String projectId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        ProjectDao.ProjectRow row = new ProjectDao.ProjectRow(projectId, tenantId, name.trim(), normalizeDescription(description),
                1, actorId, now, now);
        try {
            projectDao.create(row);
            projectDao.createVersion(UUID.randomUUID().toString(), projectId, 1, row.name(), row.description(), actorId, now);
        } catch (DuplicateKeyException exception) {
            throw error("PROJECT_NAME_EXISTS", HttpStatus.CONFLICT, "租户内项目名称已存在");
        }
        // 创建人必须立即具备项目管理能力，角色绑定仍由 IAM 保存，项目模块不持久化成员关系。
        String memberId = tenantService.requireTenantAdmin(actorId, tenantId).id();
        projectRoleService.bind(actorId, tenantId, memberId, projectId, "PROJECT_ADMIN");
        return view(row);
    }

    public List<ProjectView> list(String actorId, String tenantId) {
        tenantService.requireMember(actorId, tenantId);
        return projectDao.listByTenant(tenantId).stream()
                .filter(row -> canView(actorId, row))
                .map(this::view)
                .toList();
    }

    public ProjectView get(String actorId, String projectId) {
        ProjectDao.ProjectRow row = requireProject(projectId);
        require(actorId, row.tenantId(), row.id(), row.id(), "project.view", AuthorizationScope.ScopeType.PROJECT);
        return view(row);
    }

    @Transactional
    public ProjectView update(String actorId, String projectId, int expectedVersion, String name, String description) {
        ProjectDao.ProjectRow current = requireProject(projectId);
        require(actorId, current.tenantId(), current.id(), current.id(), "project.update", AuthorizationScope.ScopeType.PROJECT);
        validateName(name);
        if (expectedVersion < 1) {
            throw error("PROJECT_VERSION_CONFLICT", HttpStatus.CONFLICT, "项目版本不匹配");
        }
        Instant now = Instant.now();
        String normalizedDescription = normalizeDescription(description);
        try {
            if (!projectDao.update(projectId, expectedVersion, name.trim(), normalizedDescription, now)) {
                throw error("PROJECT_VERSION_CONFLICT", HttpStatus.CONFLICT, "项目版本不匹配");
            }
        } catch (DuplicateKeyException exception) {
            throw error("PROJECT_NAME_EXISTS", HttpStatus.CONFLICT, "租户内项目名称已存在");
        }
        int nextVersion = expectedVersion + 1;
        projectDao.createVersion(UUID.randomUUID().toString(), projectId, nextVersion, name.trim(), normalizedDescription, actorId, now);
        return view(requireProject(projectId));
    }

    @Transactional
    public void delete(String actorId, String projectId) {
        ProjectDao.ProjectRow current = requireProject(projectId);
        // 删除项目只能由租户范围权限决定，不能因项目管理员模板的项目范围通配而放宽。
        require(actorId, current.tenantId(), null, current.id(), "project.delete", AuthorizationScope.ScopeType.TENANT);
        projectRoleService.unbindProject(actorId, current.tenantId(), projectId);
        if (!projectDao.delete(projectId)) {
            throw error("PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "项目不存在或不可见");
        }
    }

    private boolean canView(String actorId, ProjectDao.ProjectRow row) {
        AuthorizationDecision decision = authorizationService.authorize(request(actorId, row.tenantId(), row.id(), row.id(),
                "project.view", AuthorizationScope.ScopeType.PROJECT));
        return decision.decision() == AuthorizationDecision.Decision.ALLOW;
    }

    private ProjectDao.ProjectRow requireProject(String projectId) {
        return projectDao.findById(projectId)
                .orElseThrow(() -> error("PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "项目不存在或不可见"));
    }

    private void require(String actorId, String tenantId, String projectId, String resourceId, String action,
                         AuthorizationScope.ScopeType scopeType) {
        authorizationService.requireAuthorization(request(actorId, tenantId, projectId, resourceId, action, scopeType));
    }

    private AuthorizationRequest request(String actorId, String tenantId, String projectId, String resourceId,
                                         String action, AuthorizationScope.ScopeType scopeType) {
        return new AuthorizationRequest(new AuthenticatedSubject(actorId, null, Instant.now()), PROJECT_RESOURCE, resourceId,
                action, new AuthorizationScope(scopeType, tenantId, projectId, null, null), java.util.Map.of());
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 128
                || !name.trim().matches("[\\p{IsHan}A-Za-z0-9 _.,:;()（）\\-]+")) {
            throw error("PROJECT_NAME_INVALID", HttpStatus.BAD_REQUEST, "项目名称不符合要求");
        }
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        if (description.length() > 500) {
            throw error("PROJECT_DESCRIPTION_INVALID", HttpStatus.BAD_REQUEST, "项目说明不能超过 500 个字符");
        }
        return description.trim();
    }

    private ProjectView view(ProjectDao.ProjectRow row) {
        return new ProjectView(row.id(), row.tenantId(), row.name(), row.description(), row.currentVersionNo(), row.createdAt(), row.updatedAt());
    }

    private ProjectManagementException error(String code, HttpStatus status, String message) {
        return new ProjectManagementException(code, status, message);
    }

    public record ProjectView(String id, String tenantId, String name, String description, int version,
                              Instant createdAt, Instant updatedAt) {
    }
}

package devops.iam.authorization;

import devops.iam.api.IamException;
import devops.iam.contract.AuthorizationDecision;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationService;
import devops.iam.dao.AuthorizationGrantDao;
import devops.iam.dao.ProjectRoleBindingDao;
import devops.iam.tenant.TenantService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 统一授权入口；在项目模块出现前，只依据 IAM 自身可验证的租户成员关系决策。 */
@Service
public class DefaultAuthorizationService implements AuthorizationService {
    private final TenantService tenantService;
    private final AuthorizationGrantDao grantDao;
    private final BuiltInRolePermissionPolicy rolePolicy;
    private final ProjectRoleBindingDao projectRoleBindingDao;

    public DefaultAuthorizationService(TenantService tenantService, AuthorizationGrantDao grantDao,
                                       BuiltInRolePermissionPolicy rolePolicy, ProjectRoleBindingDao projectRoleBindingDao) {
        this.tenantService = tenantService;
        this.grantDao = grantDao;
        this.rolePolicy = rolePolicy;
        this.projectRoleBindingDao = projectRoleBindingDao;
    }

    @Override
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        if (request == null || request.subject() == null || request.scope() == null
                || request.scope().scopeType() == null || blank(request.subject().userId())
                || blank(request.resourceType()) || blank(request.resourceId()) || blank(request.actionCode())) {
            return denied("INVALID_AUTHORIZATION_REQUEST");
        }
        if (request.scope().scopeType().name().equals("PLATFORM")) {
            return denied("PLATFORM_SCOPE_DEFAULT_DENY");
        }
        if (request.scope().tenantId() == null || request.scope().tenantId().isBlank()) {
            return denied("INVALID_AUTHORIZATION_REQUEST");
        }
        try {
            var member = tenantService.requireMember(request.subject().userId(), request.scope().tenantId());
            if (rolePolicy.permits(member, request.resourceType(), request.resourceId(), request.actionCode(),
                    request.scope().tenantId())) {
                return allowed("BUILT_IN_ROLE_TEMPLATE", List.of());
            }
            if (request.scope().projectId() != null) {
                var projectRole = projectRoleBindingDao.findRole(request.scope().tenantId(), member.id(), request.scope().projectId());
                if (projectRole.filter(role -> "DEVELOPER".equals(role)).isPresent()
                        && request.scope().environmentLevel() == devops.iam.contract.AuthorizationScope.EnvironmentLevel.PROD) {
                    return denied("DEVELOPER_PROD_NOT_SUPPORTED");
                }
                if (projectRole.filter(role -> permitsProjectRole(role, request)).isPresent()) {
                    return allowed("PROJECT_ROLE_TEMPLATE", List.of());
                }
            }
            List<String> grants = grantDao.findMatches(request.scope().tenantId(), member.id(), request.resourceType(),
                    request.resourceId(), request.actionCode(), request.scope().environmentLevel() == null ? null
                    : request.scope().environmentLevel().name()).stream().map(AuthorizationGrantDao.GrantRow::id).toList();
            if (grants.isEmpty() && request.scope().projectId() != null
                    && "ENVIRONMENT".equals(request.resourceType())) {
                grants = grantDao.findMatches(request.scope().tenantId(), member.id(), "PROJECT",
                        request.scope().projectId(), request.actionCode(), request.scope().environmentLevel() == null
                        ? null : request.scope().environmentLevel().name()).stream()
                        .map(AuthorizationGrantDao.GrantRow::id).toList();
            }
            if (!grants.isEmpty()) {
                return allowed("EXPLICIT_GRANT", grants);
            }
            // 项目管理员的“本人项目”归属必须由未来项目模块提供可靠适配器，当前默认拒绝。
            return denied("NO_MATCHING_PERMISSION");
        } catch (IamException exception) {
            return denied("TENANT_NOT_VISIBLE");
        }
    }

    @Override
    public void requireAuthorization(AuthorizationRequest request) {
        AuthorizationDecision decision = authorize(request);
        if (decision.decision() != AuthorizationDecision.Decision.ALLOW) {
            throw new IamException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "没有执行此操作的权限");
        }
    }

    private AuthorizationDecision allowed(String reason, List<String> grants) {
        return new AuthorizationDecision(AuthorizationDecision.Decision.ALLOW, reason, grants, "iam-v1");
    }

    private AuthorizationDecision denied(String reason) {
        return new AuthorizationDecision(AuthorizationDecision.Decision.DENY, reason, List.of(), "iam-v1");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean permitsProjectRole(String role, AuthorizationRequest request) {
        if ("PROJECT_ADMIN".equals(role)) {
            return true;
        }
        if (!"DEVELOPER".equals(role)) return false;
        if ("repository.use".equals(request.actionCode())) return true;
        return request.scope().environmentLevel() == devops.iam.contract.AuthorizationScope.EnvironmentLevel.DEV
                || request.scope().environmentLevel() == devops.iam.contract.AuthorizationScope.EnvironmentLevel.TEST
                || request.scope().environmentLevel() == devops.iam.contract.AuthorizationScope.EnvironmentLevel.STAGING;
    }
}

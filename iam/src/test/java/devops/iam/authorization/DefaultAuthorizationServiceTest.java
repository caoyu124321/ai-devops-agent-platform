package devops.iam.authorization;

import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationDecision;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationScope;
import devops.iam.dao.AuthorizationGrantDao;
import devops.iam.dao.ProjectRoleBindingDao;
import devops.iam.dao.TenantDao;
import devops.iam.tenant.TenantService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** 覆盖租户模板、项目模板、生产环境限制和最小权限拒绝。 */
@ExtendWith(MockitoExtension.class)
class DefaultAuthorizationServiceTest {
    @Mock
    private TenantService tenantService;

    @Mock
    private AuthorizationGrantDao grantDao;

    @Mock
    private BuiltInRolePermissionPolicy rolePolicy;

    @Mock
    private ProjectRoleBindingDao projectRoleBindingDao;

    @Test
    void shouldDenyPlatformScopeByDefault() {
        DefaultAuthorizationService service = service();

        AuthorizationDecision decision = service.authorize(new AuthorizationRequest(
                new AuthenticatedSubject("user-1", "session-1", Instant.now()), "PLATFORM", "platform",
                "tenant.view", new AuthorizationScope(AuthorizationScope.ScopeType.PLATFORM, null, null, null, null),
                Map.of()));

        assertEquals(AuthorizationDecision.Decision.DENY, decision.decision());
        assertEquals("PLATFORM_SCOPE_DEFAULT_DENY", decision.reasonCode());
    }

    @Test
    void shouldDenyIncompleteAuthorizationRequest() {
        AuthorizationDecision decision = service().authorize(new AuthorizationRequest(
                new AuthenticatedSubject("user-1", "session-1", Instant.now()), "PROJECT", "project-1", "",
                new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT, "tenant-1", "project-1", null, null),
                Map.of()));

        assertEquals(AuthorizationDecision.Decision.DENY, decision.decision());
        assertEquals("INVALID_AUTHORIZATION_REQUEST", decision.reasonCode());
    }

    @Test
    void shouldAllowTenantAdministratorForAnyTenantResource() {
        DefaultAuthorizationService service = service();
        TenantDao.MemberRow member = member("TENANT_ADMIN");
        when(tenantService.requireMember("user-1", "tenant-1")).thenReturn(member);
        when(rolePolicy.permits(member, "ENVIRONMENT", "environment-1", "environment.deploy", "tenant-1"))
                .thenReturn(true);

        AuthorizationDecision decision = service.authorize(environmentRequest(AuthorizationScope.EnvironmentLevel.PROD));

        assertEquals(AuthorizationDecision.Decision.ALLOW, decision.decision());
        assertEquals("BUILT_IN_ROLE_TEMPLATE", decision.reasonCode());
    }

    @Test
    void shouldDenyDeveloperProductionEvenWhenInternalGrantExists() {
        DefaultAuthorizationService service = service();
        when(tenantService.requireMember("user-1", "tenant-1")).thenReturn(member("MEMBER"));
        when(projectRoleBindingDao.findRole("tenant-1", "member-1", "project-1"))
                .thenReturn(Optional.of("DEVELOPER"));

        AuthorizationDecision decision = service.authorize(environmentRequest(AuthorizationScope.EnvironmentLevel.PROD));

        assertEquals(AuthorizationDecision.Decision.DENY, decision.decision());
        assertEquals("DEVELOPER_PROD_NOT_SUPPORTED", decision.reasonCode());
    }

    @Test
    void shouldAllowProjectAdministratorForBoundProjectProductionEnvironment() {
        DefaultAuthorizationService service = service();
        when(tenantService.requireMember("user-1", "tenant-1")).thenReturn(member("MEMBER"));
        when(projectRoleBindingDao.findRole("tenant-1", "member-1", "project-1"))
                .thenReturn(Optional.of("PROJECT_ADMIN"));

        AuthorizationDecision decision = service.authorize(environmentRequest(AuthorizationScope.EnvironmentLevel.PROD));

        assertEquals(AuthorizationDecision.Decision.ALLOW, decision.decision());
        assertEquals("PROJECT_ROLE_TEMPLATE", decision.reasonCode());
    }

    @Test
    void shouldAllowDeveloperForTestAndStagingOnly() {
        DefaultAuthorizationService service = service();
        when(tenantService.requireMember("user-1", "tenant-1")).thenReturn(member("MEMBER"));
        when(projectRoleBindingDao.findRole("tenant-1", "member-1", "project-1"))
                .thenReturn(Optional.of("DEVELOPER"));

        AuthorizationDecision decision = service.authorize(environmentRequest(AuthorizationScope.EnvironmentLevel.STAGING));

        assertEquals(AuthorizationDecision.Decision.ALLOW, decision.decision());
        assertEquals("PROJECT_ROLE_TEMPLATE", decision.reasonCode());
    }

    @Test
    void shouldAllowEnvironmentRequestFromInternalProjectScopedGrant() {
        DefaultAuthorizationService service = service();
        when(tenantService.requireMember("user-1", "tenant-1")).thenReturn(member("MEMBER"));
        when(projectRoleBindingDao.findRole("tenant-1", "member-1", "project-1")).thenReturn(Optional.empty());
        when(grantDao.findMatches("tenant-1", "member-1", "ENVIRONMENT", "environment-1", "pipeline.run", "TEST"))
                .thenReturn(List.of());
        when(grantDao.findMatches("tenant-1", "member-1", "PROJECT", "project-1", "pipeline.run", "TEST"))
                .thenReturn(List.of(new AuthorizationGrantDao.GrantRow("grant-1", "tenant-1", "member-1", "PROJECT",
                        "project-1", "pipeline.run", "TEST", "ALLOW", "admin-1", Instant.now())));

        AuthorizationDecision decision = service.authorize(new AuthorizationRequest(
                new AuthenticatedSubject("user-1", "session-1", Instant.now()), "ENVIRONMENT", "environment-1",
                "pipeline.run", new AuthorizationScope(AuthorizationScope.ScopeType.ENVIRONMENT, "tenant-1",
                "project-1", "environment-1", AuthorizationScope.EnvironmentLevel.TEST), Map.of()));

        assertEquals(AuthorizationDecision.Decision.ALLOW, decision.decision());
        assertEquals("EXPLICIT_GRANT", decision.reasonCode());
    }

    private DefaultAuthorizationService service() {
        return new DefaultAuthorizationService(tenantService, grantDao, rolePolicy, projectRoleBindingDao);
    }

    private TenantDao.MemberRow member(String roleCode) {
        return new TenantDao.MemberRow("member-1", "tenant-1", "user-1", roleCode, Instant.now());
    }

    private AuthorizationRequest environmentRequest(AuthorizationScope.EnvironmentLevel level) {
        return new AuthorizationRequest(new AuthenticatedSubject("user-1", "session-1", Instant.now()), "ENVIRONMENT",
                "environment-1", "environment.deploy", new AuthorizationScope(AuthorizationScope.ScopeType.ENVIRONMENT,
                "tenant-1", "project-1", "environment-1", level), Map.of());
    }
}

package devops.projectmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import devops.iam.authorization.ProjectRoleService;
import devops.iam.contract.AuthorizationDecision;
import devops.iam.contract.AuthorizationService;
import devops.iam.dao.TenantDao;
import devops.iam.tenant.TenantService;
import devops.projectmanagement.api.ProjectManagementException;
import devops.projectmanagement.dao.ProjectDao;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {
    @Test
    void createsProjectAndBindsCreatorAsProjectAdmin() {
        ProjectDao dao = mock(ProjectDao.class);
        AuthorizationService authorizationService = allowAll();
        TenantService tenantService = mock(TenantService.class);
        ProjectRoleService roleService = mock(ProjectRoleService.class);
        when(tenantService.requireTenantAdmin("actor", "tenant")).thenReturn(member("member", "actor"));
        doNothing().when(roleService).bind(eq("actor"), eq("tenant"), eq("member"), any(), eq("PROJECT_ADMIN"));
        ProjectService service = new ProjectService(dao, authorizationService, tenantService, roleService);

        ProjectService.ProjectView result = service.create("actor", "tenant", "订单 服务", "说明");

        assertThat(result.tenantId()).isEqualTo("tenant");
        assertThat(result.name()).isEqualTo("订单 服务");
        assertThat(result.version()).isEqualTo(1);
        verify(dao).create(any());
        verify(dao).createVersion(any(), eq(result.id()), eq(1), eq("订单 服务"), eq("说明"), eq("actor"), any());
        verify(roleService).bind("actor", "tenant", "member", result.id(), "PROJECT_ADMIN");
    }

    @Test
    void rejectsInvalidProjectNameBeforeWriting() {
        ProjectService service = new ProjectService(mock(ProjectDao.class), allowAll(), mock(TenantService.class),
                mock(ProjectRoleService.class));

        assertThatThrownBy(() -> service.create("actor", "tenant", "<script>", null))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("PROJECT_NAME_INVALID");
    }

    @Test
    void updateRejectsStaleVersion() {
        ProjectDao dao = mock(ProjectDao.class);
        ProjectDao.ProjectRow current = row("project", "tenant", 2);
        when(dao.findById("project")).thenReturn(Optional.of(current));
        when(dao.update(eq("project"), eq(1), any(), any(), any())).thenReturn(false);
        ProjectService service = new ProjectService(dao, allowAll(), mock(TenantService.class), mock(ProjectRoleService.class));

        assertThatThrownBy(() -> service.update("actor", "project", 1, "新项目", null))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("PROJECT_VERSION_CONFLICT");
    }

    @Test
    void listOnlyReturnsProjectsAllowedByIam() {
        ProjectDao dao = mock(ProjectDao.class);
        TenantService tenantService = mock(TenantService.class);
        when(dao.listByTenant("tenant")).thenReturn(List.of(row("allowed", "tenant", 1), row("hidden", "tenant", 1)));
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        when(authorizationService.authorize(any())).thenAnswer(invocation -> {
            String resourceId = invocation.getArgument(0, devops.iam.contract.AuthorizationRequest.class).resourceId();
            return "allowed".equals(resourceId) ? allowDecision() : denyDecision();
        });
        ProjectService service = new ProjectService(dao, authorizationService, tenantService, mock(ProjectRoleService.class));

        List<ProjectService.ProjectView> projects = service.list("actor", "tenant");

        assertThat(projects).extracting(ProjectService.ProjectView::id).containsExactly("allowed");
    }

    private AuthorizationService allowAll() {
        AuthorizationService service = mock(AuthorizationService.class);
        when(service.authorize(any())).thenReturn(allowDecision());
        doNothing().when(service).requireAuthorization(any());
        return service;
    }

    private AuthorizationDecision allowDecision() {
        return new AuthorizationDecision(AuthorizationDecision.Decision.ALLOW, "TEST", List.of(), "v1");
    }

    private AuthorizationDecision denyDecision() {
        return new AuthorizationDecision(AuthorizationDecision.Decision.DENY, "TEST", List.of(), "v1");
    }

    private TenantDao.MemberRow member(String id, String userId) {
        return new TenantDao.MemberRow(id, "tenant", userId, "TENANT_ADMIN", Instant.now());
    }

    private ProjectDao.ProjectRow row(String id, String tenantId, int version) {
        Instant now = Instant.now();
        return new ProjectDao.ProjectRow(id, tenantId, "项目" + id, null, version, "creator", now, now);
    }
}

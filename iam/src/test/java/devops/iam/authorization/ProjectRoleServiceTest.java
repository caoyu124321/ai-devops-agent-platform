package devops.iam.authorization;

import devops.iam.api.IamException;
import devops.iam.dao.ProjectRoleBindingDao;
import devops.iam.dao.TenantDao;
import devops.iam.event.IamEventPublisher;
import devops.iam.tenant.TenantService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 覆盖项目角色分配的租户边界、角色白名单和事件广播。 */
@ExtendWith(MockitoExtension.class)
class ProjectRoleServiceTest {
    @Mock
    private ProjectRoleBindingDao bindingDao;

    @Mock
    private TenantService tenantService;

    @Mock
    private IamEventPublisher eventPublisher;

    @Test
    void shouldBindDeveloperToOneProjectAndPublishEvent() {
        ProjectRoleService service = new ProjectRoleService(bindingDao, tenantService, eventPublisher);
        TenantDao.MemberRow administrator = new TenantDao.MemberRow("admin-member", "tenant-1", "admin",
                "TENANT_ADMIN", Instant.now());
        TenantDao.MemberRow target = new TenantDao.MemberRow("member-1", "tenant-1", "user-1", "MEMBER",
                Instant.now());
        when(tenantService.requireTenantAdmin("admin", "tenant-1")).thenReturn(administrator);
        when(tenantService.findMemberInternal("tenant-1", "member-1")).thenReturn(target);

        service.bind("admin", "tenant-1", "member-1", "project-1", "DEVELOPER");

        verify(bindingDao).create(anyString(), org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("member-1"), org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("DEVELOPER"), org.mockito.ArgumentMatchers.eq("admin"), any());
        verify(eventPublisher).publishAfterCommit(any());
    }

    @Test
    void shouldRejectUnsupportedProjectRole() {
        ProjectRoleService service = new ProjectRoleService(bindingDao, tenantService, eventPublisher);
        when(tenantService.requireTenantAdmin("admin", "tenant-1"))
                .thenReturn(new TenantDao.MemberRow("admin-member", "tenant-1", "admin", "TENANT_ADMIN", Instant.now()));
        when(tenantService.findMemberInternal("tenant-1", "member-1"))
                .thenReturn(new TenantDao.MemberRow("member-1", "tenant-1", "user-1", "MEMBER", Instant.now()));

        IamException exception = assertThrows(IamException.class,
                () -> service.bind("admin", "tenant-1", "member-1", "project-1", "OBSERVER"));

        assertEquals("PROJECT_ROLE_INVALID", exception.code());
    }

    @Test
    void shouldRevokeProjectRoleAndPublishChangeEvent() {
        ProjectRoleService service = new ProjectRoleService(bindingDao, tenantService, eventPublisher);
        when(tenantService.requireTenantAdmin("admin", "tenant-1"))
                .thenReturn(new TenantDao.MemberRow("admin-member", "tenant-1", "admin", "TENANT_ADMIN", Instant.now()));
        when(tenantService.findMemberInternal("tenant-1", "member-1"))
                .thenReturn(new TenantDao.MemberRow("member-1", "tenant-1", "user-1", "MEMBER", Instant.now()));
        when(bindingDao.deleteByMemberAndProject("tenant-1", "member-1", "project-1")).thenReturn(true);

        service.unbind("admin", "tenant-1", "member-1", "project-1");

        verify(eventPublisher).publishAfterCommit(any());
    }
}

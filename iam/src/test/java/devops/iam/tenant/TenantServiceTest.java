package devops.iam.tenant;

import devops.iam.api.IamException;
import devops.iam.dao.ProjectRoleBindingDao;
import devops.iam.dao.TenantDao;
import devops.iam.event.IamEventPublisher;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 覆盖租户成员关系、最后管理员保护和项目角色清理边界。 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    @Mock
    private TenantDao dao;

    @Mock
    private ProjectRoleBindingDao projectRoleBindingDao;

    @Mock
    private IamEventPublisher eventPublisher;

    @Test
    void shouldNotAllowLastAdministratorToLeave() {
        TenantService service = new TenantService(dao, projectRoleBindingDao, eventPublisher);
        TenantDao.MemberRow administrator = new TenantDao.MemberRow("member-1", "tenant-1", "user-1",
                "TENANT_ADMIN", Instant.now());
        when(dao.findMember("tenant-1", "user-1")).thenReturn(Optional.of(administrator));
        when(dao.countAdministrators("tenant-1")).thenReturn(1);

        IamException exception = assertThrows(IamException.class, () -> service.leave("user-1", "tenant-1"));

        assertEquals("LAST_TENANT_ADMIN", exception.code());
        verify(dao, never()).deleteMember("member-1");
    }

    @Test
    void shouldCreateCreatorAsTenantAdministratorWithoutMutableGrant() {
        TenantService service = new TenantService(dao, projectRoleBindingDao, eventPublisher);

        TenantService.TenantView tenant = service.create("user-1", "研发租户");

        assertEquals("TENANT_ADMIN", tenant.roleCode());
        verify(dao).createTenant(anyString(), org.mockito.ArgumentMatchers.eq("研发租户"),
                org.mockito.ArgumentMatchers.eq("user-1"), any());
        verify(dao).createMember(anyString(), anyString(), org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("TENANT_ADMIN"), any());
    }

    @Test
    void shouldDeleteProjectRolesWhenMemberIsRemoved() {
        TenantService service = new TenantService(dao, projectRoleBindingDao, eventPublisher);
        TenantDao.MemberRow administrator = new TenantDao.MemberRow("admin-member", "tenant-1", "admin",
                "TENANT_ADMIN", Instant.now());
        TenantDao.MemberRow target = new TenantDao.MemberRow("member-1", "tenant-1", "user-1", "MEMBER",
                Instant.now());
        when(dao.findMember("tenant-1", "admin")).thenReturn(Optional.of(administrator));
        when(dao.findMemberById("tenant-1", "member-1")).thenReturn(Optional.of(target));

        service.removeMember("admin", "tenant-1", "member-1");

        verify(projectRoleBindingDao).deleteByMember("member-1");
        verify(dao).deleteMember("member-1");
    }
}

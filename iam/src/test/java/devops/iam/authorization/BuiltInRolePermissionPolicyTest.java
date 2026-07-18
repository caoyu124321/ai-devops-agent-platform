package devops.iam.authorization;

import devops.iam.dao.TenantDao;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 证明租户管理员模板不依赖具体业务资源或动作，普通成员默认无租户级模板权限。 */
class BuiltInRolePermissionPolicyTest {
    private final BuiltInRolePermissionPolicy policy = new BuiltInRolePermissionPolicy();

    @Test
    void shouldPermitTenantAdministratorForAbstractTenantResource() {
        TenantDao.MemberRow member = new TenantDao.MemberRow("member-1", "tenant-1", "user-1", "TENANT_ADMIN",
                Instant.now());

        assertTrue(policy.permits(member, "ARBITRARY_RESOURCE", "resource-1", "arbitrary.action", "tenant-1"));
    }

    @Test
    void shouldNotPermitOrdinaryMemberWithoutProjectBinding() {
        TenantDao.MemberRow member = new TenantDao.MemberRow("member-1", "tenant-1", "user-1", "MEMBER",
                Instant.now());

        assertFalse(policy.permits(member, "PROJECT", "project-1", "pipeline.run", "tenant-1"));
    }
}

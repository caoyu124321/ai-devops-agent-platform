package devops.iam.authorization;

import devops.iam.dao.TenantDao;
import org.springframework.stereotype.Component;

/** 官方内置角色的不可变权限模板；不读取项目等业务模块数据。 */
@Component
public class BuiltInRolePermissionPolicy {
    public boolean permits(TenantDao.MemberRow member, String resourceType, String resourceId, String actionCode,
                           String tenantId) {
        /* 租户管理员模板覆盖所属租户中的所有抽象资源和动作，IAM 不解释其业务语义。 */
        return "TENANT_ADMIN".equals(member.roleCode()) && tenantId != null && !tenantId.isBlank();
    }
}

package devops.iam.contract;

import java.util.Map;

/** 后续模块通过此对象请求授权，禁止以角色名称直接进行业务判断。 */
public record AuthorizationRequest(
        AuthenticatedSubject subject,
        String resourceType,
        String resourceId,
        String actionCode,
        AuthorizationScope scope,
        Map<String, String> context) {
}

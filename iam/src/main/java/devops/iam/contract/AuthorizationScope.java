package devops.iam.contract;

/** 授权范围及调用方提供的抽象资源归属上下文。 */
public record AuthorizationScope(
        ScopeType scopeType,
        String tenantId,
        String projectId,
        String environmentId,
        EnvironmentLevel environmentLevel) {
    public enum ScopeType { PLATFORM, TENANT, PROJECT, ENVIRONMENT }

    public enum EnvironmentLevel { DEV, TEST, STAGING, PROD }
}

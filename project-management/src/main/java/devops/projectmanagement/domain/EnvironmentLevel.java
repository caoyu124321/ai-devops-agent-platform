package devops.projectmanagement.domain;

/** 项目环境分级，与 IAM 授权范围中的分级语义保持一致。 */
public enum EnvironmentLevel {
    DEV,
    TEST,
    STAGING,
    PROD
}

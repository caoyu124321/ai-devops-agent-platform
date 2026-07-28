package devops.projectmanagement.domain;

/** MVP 支持的部署目标类型；新增类型必须先完成独立规格评审。 */
public enum EnvironmentTargetType {
    KUBERNETES,
    LINUX_HOST,
    WINDOWS_HOST
}

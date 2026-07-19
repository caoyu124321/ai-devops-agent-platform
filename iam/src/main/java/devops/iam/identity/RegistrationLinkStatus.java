package devops.iam.identity;

/** 安全注册链接的有限生命周期，只有待完成状态能够创建用户。 */
public enum RegistrationLinkStatus {
    PENDING,
    COMPLETED,
    EXPIRED,
    CANCELLED
}

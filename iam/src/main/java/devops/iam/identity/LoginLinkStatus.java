package devops.iam.identity;

/** 本机登录链接的有限状态，完成后不允许再次提交密码。 */
public enum LoginLinkStatus {
    PENDING,
    COMPLETED,
    EXPIRED
}

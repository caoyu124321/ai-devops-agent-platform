package devops.mcp.identity;

/** 可安全返回给 Codex 的注册链接状态；用户摘要只会在注册链接完成后出现。 */
public record RegistrationLinkStatus(String id, String status, String expiresAt, UserSummary user) {
}

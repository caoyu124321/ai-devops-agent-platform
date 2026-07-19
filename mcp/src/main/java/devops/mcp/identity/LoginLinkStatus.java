package devops.mcp.identity;

/** 可安全返回给 Codex 的登录链接状态，不含链接或会话令牌。 */
public record LoginLinkStatus(String id, String status, String expiresAt, String sessionExpiresAt, UserSummary user) {
}

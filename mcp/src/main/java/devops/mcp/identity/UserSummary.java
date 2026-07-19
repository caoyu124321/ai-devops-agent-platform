package devops.mcp.identity;

/** 可安全返回给 Codex 的平台用户摘要。 */
public record UserSummary(String id, String username, String email) {
}

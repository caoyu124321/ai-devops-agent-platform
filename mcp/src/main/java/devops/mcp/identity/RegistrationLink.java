package devops.mcp.identity;

/** MCP 内部保存注册链接持有者令牌，仅用于后续状态查询，绝不返回给 Codex。 */
public record RegistrationLink(String id, String url, String token, String expiresAt) {
}

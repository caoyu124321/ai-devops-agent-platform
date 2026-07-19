package devops.mcp.identity;

/** 登录链接与 MCP 预生成会话令牌仅保留在 MCP 进程内，浏览器不会取得会话令牌。 */
public record LoginLink(String id, String url, String token, String sessionToken, String expiresAt) {
}

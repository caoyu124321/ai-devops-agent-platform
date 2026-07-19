package devops.mcp.credential;

/**
 * 仅在当前 Windows 用户凭据管理器中保存的待完成登录链接；会话令牌原文不写入工作区或平台数据库。
 */
public record PendingLoginLink(String id, String url, String token, String sessionToken, String expiresAt) {
}

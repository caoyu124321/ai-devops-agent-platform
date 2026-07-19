package devops.mcp.identity;

import java.time.Instant;

/** 登录成功后的内部结果，Token 仅供凭据仓库使用。 */
public record LoginResult(String token, Instant expiresAt, UserSummary user) {
}

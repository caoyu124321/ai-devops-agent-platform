package devops.mcp.identity;

import java.time.Instant;

/** Windows 凭据管理器中的活动会话，禁止写入日志。 */
public record StoredSession(String token, String userId, Instant expiresAt) {
}

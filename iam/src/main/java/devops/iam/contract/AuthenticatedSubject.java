package devops.iam.contract;

import java.time.Instant;

/** 已认证主体；公开契约不携带 Token、密码或凭据。 */
public record AuthenticatedSubject(String userId, String sessionId, Instant authenticatedAt) {
}

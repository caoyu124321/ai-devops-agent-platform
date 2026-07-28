package devops.iam.oauth;

import java.time.Duration;
import java.util.Set;

/** OAuth 协议中重复使用的固定值集中管理，避免生命周期规则散落在业务代码中。 */
final class OAuthConstants {
    static final String TOKEN_TYPE_BEARER = "Bearer";
    static final String S256 = "S256";
    static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    static final String GRANT_REFRESH_TOKEN = "refresh_token";
    static final String SCOPE_OPENID = "openid";
    static final String SCOPE_PROFILE = "profile";
    static final String SCOPE_EMAIL = "email";
    static final String SCOPE_OFFLINE_ACCESS = "offline_access";
    static final String SCOPE_MCP_TOOLS = "mcp.tools";
    static final Set<String> SUPPORTED_SCOPES = Set.of(SCOPE_OPENID, SCOPE_PROFILE, SCOPE_EMAIL, SCOPE_OFFLINE_ACCESS, SCOPE_MCP_TOOLS);
    static final Duration AUTHORIZATION_CODE_TTL = Duration.ofMinutes(5);
    static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    static final Duration REFRESH_GRANT_TTL = Duration.ofDays(30);
    static final Duration REFRESH_IDLE_TTL = Duration.ofDays(7);
    static final Duration BROWSER_SESSION_TTL = Duration.ofHours(8);

    private OAuthConstants() {
    }
}

package devops.mcp.identity;

import devops.mcp.credential.CredentialStore;
import devops.mcp.credential.PendingLoginLink;
import devops.mcp.platform.PlatformApi;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 统一处理 Codex 本地会话的保存、复用与失效清理，避免工具层接触 Token。 */
public class McpIdentityService {
    private final String baseUrl;
    private final PlatformApi platformApi;
    private final CredentialStore credentialStore;
    private final Clock clock;
    private RegistrationLink activeRegistrationLink;
    private LoginLink activeLoginLink;
    private final SecureRandom random = new SecureRandom();

    public McpIdentityService(String baseUrl, PlatformApi platformApi, CredentialStore credentialStore) {
        this(baseUrl, platformApi, credentialStore, Clock.systemUTC());
    }

    McpIdentityService(String baseUrl, PlatformApi platformApi, CredentialStore credentialStore, Clock clock) {
        this.baseUrl = baseUrl;
        this.platformApi = platformApi;
        this.credentialStore = credentialStore;
        this.clock = clock;
    }

    /**
     * 注册链接只在 MCP 进程内暂存，令牌不写入凭据管理器，避免与平台会话 Token 混淆。
     */
    public synchronized RegistrationLink createRegistrationLink() {
        activeRegistrationLink = platformApi.createRegistrationLink();
        return activeRegistrationLink;
    }

    public synchronized RegistrationLinkStatus registrationLinkStatus() {
        if (activeRegistrationLink == null) {
            throw new McpIdentityException(McpErrorCode.REGISTRATION_LINK_NOT_FOUND, "尚未创建注册链接。");
        }
        return platformApi.registrationLinkStatus(activeRegistrationLink.id(), activeRegistrationLink.token());
    }

    /**
     * MCP 自行生成平台会话令牌并仅提交哈希，确保浏览器完成登录时无法取得平台会话令牌原文。
     */
    public synchronized LoginLink createLoginLink() {
        String sessionToken = newToken();
        LoginLink platformLink = platformApi.createLoginLink(hash(sessionToken));
        activeLoginLink = new LoginLink(platformLink.id(), platformLink.url(), platformLink.token(), sessionToken,
                platformLink.expiresAt());
        credentialStore.replacePendingLoginLink(baseUrl, new PendingLoginLink(activeLoginLink.id(), activeLoginLink.url(),
                activeLoginLink.token(), activeLoginLink.sessionToken(), activeLoginLink.expiresAt()));
        return activeLoginLink;
    }

    public synchronized LoginLinkStatus loginLinkStatus() {
        LoginLink link = activeLoginLink == null ? pendingLoginLink() : activeLoginLink;
        LoginLinkStatus status = platformApi.loginLinkStatus(link.id(), link.token());
        if ("COMPLETED".equals(status.status())) {
            if (status.user() == null || status.sessionExpiresAt() == null) {
                throw new McpIdentityException(McpErrorCode.BACKEND_UNAVAILABLE, "平台登录链接响应格式异常。");
            }
            credentialStore.replaceActive(baseUrl, new StoredSession(link.sessionToken(), status.user().id(),
                    Instant.parse(status.sessionExpiresAt())));
            credentialStore.clearPendingLoginLink(baseUrl);
            activeLoginLink = null;
        } else if ("EXPIRED".equals(status.status())) {
            credentialStore.clearPendingLoginLink(baseUrl);
            activeLoginLink = null;
        }
        return status;
    }

    public LoginView login(String login, String password) {
        LoginResult result = platformApi.login(login, password);
        credentialStore.replaceActive(baseUrl, new StoredSession(result.token(), result.user().id(), result.expiresAt()));
        return new LoginView(result.user(), result.expiresAt());
    }

    public UserSummary currentUser() {
        return currentLogin().user();
    }

    /**
     * 查询当前活动会话时同时返回其失效时间，供状态工具在没有待完成链接时恢复登录状态。
     */
    public LoginView currentLogin() {
        StoredSession session = activeSession();
        try {
            return new LoginView(platformApi.currentUser(session.token()), session.expiresAt());
        } catch (McpIdentityException exception) {
            if (exception.code() == McpErrorCode.SESSION_EXPIRED) {
                credentialStore.clearActive(baseUrl);
            }
            throw exception;
        }
    }

    public void logout() {
        try {
            credentialStore.readActive(baseUrl).ifPresent(session -> logoutRemote(session.token()));
        } finally {
            credentialStore.clearActive(baseUrl);
        }
    }

    private void logoutRemote(String token) {
        try {
            platformApi.logout(token);
        } catch (McpIdentityException exception) {
            // 登出以清理本地凭据为最终结果，远端已失效或暂时不可达均不阻断该结果。
        }
    }

    private StoredSession activeSession() {
        StoredSession session = credentialStore.readActive(baseUrl)
                .orElseThrow(() -> new McpIdentityException(McpErrorCode.NOT_LOGGED_IN, "请先登录 AI DevOps 平台。"));
        if (!session.expiresAt().isAfter(Instant.now(clock))) {
            credentialStore.clearActive(baseUrl);
            throw new McpIdentityException(McpErrorCode.SESSION_EXPIRED, "登录已失效，请重新登录。");
        }
        return session;
    }

    private LoginLink pendingLoginLink() {
        PendingLoginLink link = credentialStore.readPendingLoginLink(baseUrl)
                .orElseThrow(() -> new McpIdentityException(McpErrorCode.LOGIN_LINK_NOT_FOUND, "尚未创建登录链接。"));
        return new LoginLink(link.id(), link.url(), link.token(), link.sessionToken(), link.expiresAt());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    /** 工具输出只返回用户摘要与失效时间，Token 不可离开身份服务。 */
    public record LoginView(UserSummary user, Instant expiresAt) {
    }
}

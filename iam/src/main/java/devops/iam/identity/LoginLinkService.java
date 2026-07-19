package devops.iam.identity;

import devops.iam.api.IamException;
import devops.iam.dao.LoginLinkDao;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将浏览器密码登录与 MCP 会话令牌交接分离：浏览器只有链接令牌，MCP 才保留平台会话令牌原文。
 */
@Service
public class LoginLinkService {
    private static final Duration LINK_TTL = Duration.ofMinutes(15);
    private static final String LOCALHOST = "localhost";
    private static final String LOOPBACK_V4 = "127.0.0.1";
    private static final String LOOPBACK_V6 = "::1";
    private static final String CLIENT_SUMMARY = "Codex local browser login";
    private final LoginLinkDao dao;
    private final IdentityService identityService;
    private final SecureRandom random;
    private final Clock clock;

    @Autowired
    public LoginLinkService(LoginLinkDao dao, IdentityService identityService) {
        this(dao, identityService, new SecureRandom(), Clock.systemUTC());
    }

    LoginLinkService(LoginLinkDao dao, IdentityService identityService, SecureRandom random, Clock clock) {
        this.dao = dao;
        this.identityService = identityService;
        this.random = random;
        this.clock = clock;
    }

    @Transactional
    public LinkCreation create(String platformBaseUrl, String sessionTokenHash) {
        URI baseUri = validateLoopbackUrl(platformBaseUrl);
        validateSessionTokenHash(sessionTokenHash);
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(LINK_TTL);
        String id = UUID.randomUUID().toString();
        String token = newLinkToken();
        dao.create(id, hash(token), sessionTokenHash, now, expiresAt);
        String url = baseUri.resolve("/api/v1/auth/login-links/" + id + "/form?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8)).toString();
        return new LinkCreation(id, url, expiresAt);
    }

    @Transactional
    public LinkView status(String id, String token) {
        LoginLinkDao.LinkRow link = checkedLink(id, token, false);
        if (isPendingAndExpired(link, Instant.now(clock))) {
            dao.expire(link.id());
            return new LinkView(link.id(), LoginLinkStatus.EXPIRED, link.expiresAt(), null, null);
        }
        return new LinkView(link.id(), link.status(), link.expiresAt(), completedUser(link), link.sessionExpiresAt());
    }

    @Transactional
    public void requirePending(String id, String token) {
        LoginLinkDao.LinkRow link = checkedLink(id, token, false);
        if (link.status() != LoginLinkStatus.PENDING || isPendingAndExpired(link, Instant.now(clock))) {
            if (isPendingAndExpired(link, Instant.now(clock))) {
                dao.expire(link.id());
            }
            throw unavailable();
        }
    }

    @Transactional
    public IdentityService.SessionLoginView complete(String id, String token, String login, String password) {
        LoginLinkDao.LinkRow link = checkedLink(id, token, true);
        Instant now = Instant.now(clock);
        if (link.status() != LoginLinkStatus.PENDING || isPendingAndExpired(link, now)) {
            if (isPendingAndExpired(link, now)) {
                dao.expire(link.id());
            }
            throw unavailable();
        }
        // 只把 MCP 预生成令牌的哈希交给 IAM，令牌原文不会被浏览器或数据库接触。
        IdentityService.SessionLoginView session = identityService.loginWithSessionTokenHash(login, password,
                link.sessionTokenHash(), CLIENT_SUMMARY);
        if (!dao.complete(link.id(), now, session.expiresAt(), session.user().id())) {
            throw unavailable();
        }
        return session;
    }

    private LoginLinkDao.LinkRow checkedLink(String id, String token, boolean forUpdate) {
        if (id == null || token == null || token.isBlank()) {
            throw unavailable();
        }
        LoginLinkDao.LinkRow link = (forUpdate ? dao.findByIdForUpdate(id) : dao.findById(id))
                .orElseThrow(this::unavailable);
        if (!MessageDigest.isEqual(link.tokenHash().getBytes(StandardCharsets.US_ASCII), hash(token).getBytes(StandardCharsets.US_ASCII))) {
            throw unavailable();
        }
        return link;
    }

    private IdentityService.UserView completedUser(LoginLinkDao.LinkRow link) {
        if (link.status() != LoginLinkStatus.COMPLETED || link.userId() == null) {
            return null;
        }
        return identityService.findUserSummary(link.userId()).orElse(null);
    }

    private boolean isPendingAndExpired(LoginLinkDao.LinkRow link, Instant now) {
        return link.status() == LoginLinkStatus.PENDING && !link.expiresAt().isAfter(now);
    }

    private void validateSessionTokenHash(String sessionTokenHash) {
        if (sessionTokenHash == null) {
            throw unavailable();
        }
        try {
            if (Base64.getDecoder().decode(sessionTokenHash).length != 32) {
                throw unavailable();
            }
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private String newLinkToken() {
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

    private URI validateLoopbackUrl(String platformBaseUrl) {
        URI uri = URI.create(platformBaseUrl);
        String host = uri.getHost();
        boolean loopback = LOCALHOST.equalsIgnoreCase(host) || LOOPBACK_V4.equals(host) || LOOPBACK_V6.equals(host);
        if (!loopback || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("登录链接只能指向本机 AI DevOps 服务。");
        }
        return uri;
    }

    private IamException unavailable() {
        return new IamException("LOGIN_LINK_UNAVAILABLE", HttpStatus.NOT_FOUND, "登录链接不可用或已失效");
    }

    public record LinkCreation(String id, String url, Instant expiresAt) {
    }

    public record LinkView(String id, LoginLinkStatus status, Instant expiresAt, IdentityService.UserView user,
                           Instant sessionExpiresAt) {
    }
}

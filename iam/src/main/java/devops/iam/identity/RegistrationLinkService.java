package devops.iam.identity;

import devops.iam.api.IamException;
import devops.iam.dao.RegistrationLinkDao;
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
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理浏览器安全注册链接。令牌只作为持有者证明使用，数据库只保存其哈希，密码始终直接交给身份服务。
 */
@Service
public class RegistrationLinkService {
    private static final Duration LINK_TTL = Duration.ofMinutes(15);
    private static final String LOCALHOST = "localhost";
    private static final String LOOPBACK_V4 = "127.0.0.1";
    private static final String LOOPBACK_V6 = "::1";
    private final RegistrationLinkDao dao;
    private final IdentityService identityService;
    private final SecureRandom random;
    private final Clock clock;

    @Autowired
    public RegistrationLinkService(RegistrationLinkDao dao, IdentityService identityService) {
        this(dao, identityService, new SecureRandom(), Clock.systemUTC());
    }

    RegistrationLinkService(RegistrationLinkDao dao, IdentityService identityService, SecureRandom random, Clock clock) {
        this.dao = dao;
        this.identityService = identityService;
        this.random = random;
        this.clock = clock;
    }

    @Transactional
    public LinkCreation create(String platformBaseUrl) {
        URI baseUri = validateLoopbackUrl(platformBaseUrl);
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(LINK_TTL);
        String id = UUID.randomUUID().toString();
        String token = newToken();
        dao.create(id, hash(token), now, expiresAt);
        String formUrl = baseUri.resolve("/api/v1/auth/registration-links/" + id + "/form?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8)).toString();
        return new LinkCreation(id, formUrl, expiresAt);
    }

    @Transactional
    public LinkView status(String id, String token) {
        RegistrationLinkDao.LinkRow link = checkedLink(id, token, false);
        if (isPendingAndExpired(link, Instant.now(clock))) {
            dao.expire(link.id());
            return new LinkView(link.id(), RegistrationLinkStatus.EXPIRED, link.expiresAt(), null);
        }
        return new LinkView(link.id(), link.status(), link.expiresAt(), registeredUser(link));
    }

    @Transactional
    public void requirePending(String id, String token) {
        RegistrationLinkDao.LinkRow link = checkedLink(id, token, false);
        Instant now = Instant.now(clock);
        if (link.status() != RegistrationLinkStatus.PENDING || isPendingAndExpired(link, now)) {
            if (isPendingAndExpired(link, now)) {
                dao.expire(link.id());
            }
            throw unavailable();
        }
    }

    @Transactional
    public IdentityService.UserView complete(String id, String token, String username, String email, String password) {
        RegistrationLinkDao.LinkRow link = checkedLink(id, token, true);
        Instant now = Instant.now(clock);
        if (link.status() != RegistrationLinkStatus.PENDING || isPendingAndExpired(link, now)) {
            if (isPendingAndExpired(link, now)) {
                dao.expire(link.id());
            }
            throw unavailable();
        }
        // 同一事务中的行锁保证链接只能有一个提交者创建用户，避免并发双提交产生歧义。
        IdentityService.UserView user = identityService.register(username, email, password);
        if (!dao.complete(link.id(), now, user.id())) {
            throw unavailable();
        }
        return user;
    }

    private RegistrationLinkDao.LinkRow checkedLink(String id, String token, boolean forUpdate) {
        if (id == null || token == null || token.isBlank()) {
            throw unavailable();
        }
        RegistrationLinkDao.LinkRow link = (forUpdate ? dao.findByIdForUpdate(id) : dao.findById(id))
                .orElseThrow(this::unavailable);
        if (!constantTimeEquals(link.tokenHash(), hash(token))) {
            throw unavailable();
        }
        return link;
    }

    private IdentityService.UserView registeredUser(RegistrationLinkDao.LinkRow link) {
        if (link.status() != RegistrationLinkStatus.COMPLETED || link.registeredUserId() == null) {
            return null;
        }
        return identityService.findUserSummary(link.registeredUserId()).orElse(null);
    }

    private boolean isPendingAndExpired(RegistrationLinkDao.LinkRow link, Instant now) {
        return link.status() == RegistrationLinkStatus.PENDING && !link.expiresAt().isAfter(now);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private URI validateLoopbackUrl(String platformBaseUrl) {
        URI uri = URI.create(platformBaseUrl);
        String host = uri.getHost();
        boolean loopback = LOCALHOST.equalsIgnoreCase(host) || LOOPBACK_V4.equals(host) || LOOPBACK_V6.equals(host);
        if (!loopback || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("注册链接只能指向本机 AI DevOps 服务。");
        }
        return uri;
    }

    private IamException unavailable() {
        return new IamException("REGISTRATION_LINK_UNAVAILABLE", HttpStatus.NOT_FOUND, "注册链接不可用或已失效");
    }

    public record LinkCreation(String id, String url, Instant expiresAt) {
    }

    public record LinkView(String id, RegistrationLinkStatus status, Instant expiresAt, IdentityService.UserView user) {
    }
}

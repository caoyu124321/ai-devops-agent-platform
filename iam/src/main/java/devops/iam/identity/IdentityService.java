package devops.iam.identity;

import devops.iam.api.IamException;
import devops.iam.dao.IdentityDao;
import devops.iam.event.IamEventPublisher;
import devops.iam.event.PasswordChangedEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 注册、登录和会话的业务规则，密码与 Token 原文绝不进入日志或数据库。 */
@Service
public class IdentityService {
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private final IdentityDao dao;
    private final IamEventPublisher eventPublisher;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public IdentityService(IdentityDao dao, IamEventPublisher eventPublisher) {
        this.dao = dao;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserView register(String username, String email, String password) {
        validatePassword(password);
        if (username == null || username.isBlank() || username.length() > 64
                || email == null || !email.contains("@") || email.length() > 254) {
            throw bad("VALIDATION_FAILED", "注册信息不符合要求");
        }
        String id = UUID.randomUUID().toString();
        dao.createUser(id, username.trim(), email.trim(), encoder.encode(password), Instant.now());
        return new UserView(id, username.trim(), email.trim());
    }

    @Transactional
    public LoginView login(String login, String password, String clientSummary) {
        var user = dao.findUser(login).orElseThrow(() -> bad("LOGIN_FAILED", "登录失败"));
        Instant now = Instant.now();
        var lock = dao.findLock(user.id());
        if (lock.lockedUntil() != null && lock.lockedUntil().isAfter(now)) {
            throw bad("LOGIN_FAILED", "登录失败");
        }
        if (!encoder.matches(password, user.passwordHash())) {
            int count = lock.failedCount() + 1;
            dao.recordFailure(user.id(), count, now, count >= 5 ? now.plus(Duration.ofMinutes(15)) : null);
            throw bad("LOGIN_FAILED", "登录失败");
        }
        dao.clearFailures(user.id());
        String token = newToken();
        String summary = clientSummary == null ? null : clientSummary.substring(0, Math.min(255, clientSummary.length()));
        dao.createSession(UUID.randomUUID().toString(), user.id(), hash(token), now, now.plus(SESSION_TTL), summary);
        return new LoginView(token, SESSION_TTL.toSeconds(), new UserView(user.id(), user.username(), user.email()));
    }

    public SessionPrincipal authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw bad("AUTHENTICATION_REQUIRED", "需要登录");
        }
        var session = dao.findActiveSession(hash(token), Instant.now())
                .orElseThrow(() -> bad("AUTHENTICATION_REQUIRED", "需要登录"));
        var user = dao.findUserById(session.userId())
                .orElseThrow(() -> bad("AUTHENTICATION_REQUIRED", "需要登录"));
        return new SessionPrincipal(session.id(), new UserView(user.id(), user.username(), user.email()));
    }

    @Transactional
    public void logout(String sessionId) {
        dao.revokeSession(sessionId, Instant.now(), "LOGOUT");
    }

    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword) {
        var user = dao.findUserById(userId).orElseThrow(() -> bad("AUTHENTICATION_REQUIRED", "需要登录"));
        if (!encoder.matches(oldPassword, user.passwordHash())) {
            throw bad("PASSWORD_INCORRECT", "原密码错误");
        }
        validatePassword(newPassword);
        Instant now = Instant.now();
        dao.updatePassword(userId, encoder.encode(newPassword), now);
        dao.revokeAll(userId, now);
        eventPublisher.publishAfterCommit(new PasswordChangedEvent(userId, now));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw bad("PASSWORD_POLICY_VIOLATION", "密码至少 8 位且必须包含字母和数字");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) { try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException("无法计算 Token 哈希", ex); } }

    private IamException bad(String code, String message) { return new IamException(code, HttpStatus.UNAUTHORIZED.equals(HttpStatus.UNAUTHORIZED) && code.startsWith("AUTH") ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST, message); }

    public record UserView(String id, String username, String email) { }
    public record LoginView(String token, long expiresInSeconds, UserView user) { }
    public record SessionPrincipal(String sessionId, UserView user) { }
}

package devops.iam.identity;

import devops.iam.api.IamException;
import devops.iam.dao.LoginLinkDao;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证浏览器登录链接只使用 MCP 提供的令牌哈希，并保持单次完成语义。 */
class LoginLinkServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");
    private static final String SESSION_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void shouldCreateLoopbackLinkWithoutPersistingRawSessionToken() {
        LoginLinkDao dao = mock(LoginLinkDao.class);
        LoginLinkService service = service(dao, mock(IdentityService.class));

        LoginLinkService.LinkCreation link = service.create("http://127.0.0.1:8080", SESSION_HASH);

        String linkToken = link.url().substring(link.url().indexOf("token=") + "token=".length());
        org.mockito.ArgumentCaptor<String> linkHash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(dao).create(anyString(), linkHash.capture(), eq(SESSION_HASH), eq(NOW), eq(NOW.plusSeconds(900)));
        assertNotEquals(linkToken, linkHash.getValue());
    }

    @Test
    void shouldCreateSessionFromPreGeneratedHashOnceBrowserCredentialsAreValid() {
        LoginLinkDao dao = mock(LoginLinkDao.class);
        IdentityService identity = mock(IdentityService.class);
        LoginLinkService service = service(dao, identity);
        LoginLinkDao.LinkRow link = new LoginLinkDao.LinkRow("link", hash("holder"), SESSION_HASH, LoginLinkStatus.PENDING,
                NOW.minusSeconds(60), NOW.plusSeconds(840), null, null, null);
        when(dao.findByIdForUpdate("link")).thenReturn(Optional.of(link));
        IdentityService.SessionLoginView session = new IdentityService.SessionLoginView(
                new IdentityService.UserView("user-1", "demo", "demo@example.com"), NOW.plusSeconds(86400));
        when(identity.loginWithSessionTokenHash("demo", "Demo1234", SESSION_HASH, "Codex local browser login"))
                .thenReturn(session);
        when(dao.complete("link", NOW, NOW.plusSeconds(86400), "user-1")).thenReturn(true);

        IdentityService.SessionLoginView result = service.complete("link", "holder", "demo", "Demo1234");

        assertEquals("user-1", result.user().id());
        verify(dao).complete("link", NOW, NOW.plusSeconds(86400), "user-1");
    }

    @Test
    void shouldRejectExpiredLinkBeforeCheckingBrowserPassword() {
        LoginLinkDao dao = mock(LoginLinkDao.class);
        IdentityService identity = mock(IdentityService.class);
        LoginLinkService service = service(dao, identity);
        LoginLinkDao.LinkRow link = new LoginLinkDao.LinkRow("link", hash("holder"), SESSION_HASH, LoginLinkStatus.PENDING,
                NOW.minusSeconds(901), NOW.minusSeconds(1), null, null, null);
        when(dao.findByIdForUpdate("link")).thenReturn(Optional.of(link));

        IamException exception = assertThrows(IamException.class,
                () -> service.complete("link", "holder", "demo", "Demo1234"));

        assertEquals("LOGIN_LINK_UNAVAILABLE", exception.code());
        verify(identity, never()).loginWithSessionTokenHash(anyString(), anyString(), anyString(), anyString());
    }

    private LoginLinkService service(LoginLinkDao dao, IdentityService identity) {
        return new LoginLinkService(dao, identity, new java.security.SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private String hash(String value) {
        try {
            return java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

package devops.iam.identity;

import devops.iam.api.IamException;
import devops.iam.dao.RegistrationLinkDao;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 覆盖注册链接一次性、过期和密码不离开浏览器提交边界的核心规则。 */
class RegistrationLinkServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void shouldCreateLoopbackLinkAndPersistOnlyTokenHash() {
        RegistrationLinkDao dao = mock(RegistrationLinkDao.class);
        RegistrationLinkService service = service(dao, mock(IdentityService.class));

        RegistrationLinkService.LinkCreation link = service.create("http://127.0.0.1:8080");

        ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
        verify(dao).create(anyString(), tokenHash.capture(), eq(NOW), eq(NOW.plusSeconds(900)));
        String token = link.url().substring(link.url().indexOf("token=") + "token=".length());
        assertNotEquals(token, tokenHash.getValue());
        assertEquals("http://127.0.0.1", java.net.URI.create(link.url()).getScheme() + "://" + java.net.URI.create(link.url()).getHost());
    }

    @Test
    void shouldRejectExpiredLinkBeforePassingPasswordToIdentityService() {
        RegistrationLinkDao dao = mock(RegistrationLinkDao.class);
        IdentityService identityService = mock(IdentityService.class);
        RegistrationLinkService service = service(dao, identityService);
        RegistrationLinkDao.LinkRow row = new RegistrationLinkDao.LinkRow("link", hash("token"), RegistrationLinkStatus.PENDING,
                NOW.minusSeconds(901), NOW.minusSeconds(1), null, null);
        when(dao.findByIdForUpdate("link")).thenReturn(Optional.of(row));

        IamException exception = assertThrows(IamException.class,
                () -> service.complete("link", "token", "user", "user@example.com", "Demo1234"));

        assertEquals("REGISTRATION_LINK_UNAVAILABLE", exception.code());
        verify(identityService, never()).register(anyString(), anyString(), anyString());
        verify(dao).expire("link");
    }

    @Test
    void shouldCompletePendingLinkExactlyOnceAfterCreatingSafeUser() {
        RegistrationLinkDao dao = mock(RegistrationLinkDao.class);
        IdentityService identityService = mock(IdentityService.class);
        RegistrationLinkService service = service(dao, identityService);
        RegistrationLinkDao.LinkRow row = new RegistrationLinkDao.LinkRow("link", hash("token"), RegistrationLinkStatus.PENDING,
                NOW.minusSeconds(60), NOW.plusSeconds(840), null, null);
        when(dao.findByIdForUpdate("link")).thenReturn(Optional.of(row));
        when(identityService.register("user", "user@example.com", "Demo1234"))
                .thenReturn(new IdentityService.UserView("user-1", "user", "user@example.com"));
        when(dao.complete("link", NOW, "user-1")).thenReturn(true);

        IdentityService.UserView user = service.complete("link", "token", "user", "user@example.com", "Demo1234");

        assertEquals("user-1", user.id());
        verify(dao).complete("link", NOW, "user-1");
    }

    @Test
    void shouldExposeUserOnlyAfterCompletedLink() {
        RegistrationLinkDao dao = mock(RegistrationLinkDao.class);
        IdentityService identityService = mock(IdentityService.class);
        RegistrationLinkService service = service(dao, identityService);
        RegistrationLinkDao.LinkRow row = new RegistrationLinkDao.LinkRow("link", hash("token"), RegistrationLinkStatus.COMPLETED,
                NOW.minusSeconds(60), NOW.plusSeconds(840), NOW.minusSeconds(30), "user-1");
        when(dao.findById("link")).thenReturn(Optional.of(row));
        when(identityService.findUserSummary("user-1")).thenReturn(Optional.of(new IdentityService.UserView("user-1", "user", "user@example.com")));

        RegistrationLinkService.LinkView view = service.status("link", "token");

        assertEquals(RegistrationLinkStatus.COMPLETED, view.status());
        assertEquals("user", view.user().username());
    }

    @Test
    void shouldNotExposeUserForPendingLink() {
        RegistrationLinkDao dao = mock(RegistrationLinkDao.class);
        RegistrationLinkService service = service(dao, mock(IdentityService.class));
        RegistrationLinkDao.LinkRow row = new RegistrationLinkDao.LinkRow("link", hash("token"), RegistrationLinkStatus.PENDING,
                NOW.minusSeconds(60), NOW.plusSeconds(840), null, null);
        when(dao.findById("link")).thenReturn(Optional.of(row));

        RegistrationLinkService.LinkView view = service.status("link", "token");

        assertEquals(RegistrationLinkStatus.PENDING, view.status());
        assertNull(view.user());
    }

    private RegistrationLinkService service(RegistrationLinkDao dao, IdentityService identityService) {
        return new RegistrationLinkService(dao, identityService, new java.security.SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));
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

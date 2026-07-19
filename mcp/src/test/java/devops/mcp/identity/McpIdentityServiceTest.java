package devops.mcp.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import devops.mcp.credential.InMemoryCredentialStore;
import devops.mcp.platform.PlatformApi;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class McpIdentityServiceTest {
    private static final String BASE_URL = "http://127.0.0.1:8080";
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void shouldStoreTokenAfterLoginButReturnOnlySafeUserView() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        FakePlatformApi api = new FakePlatformApi();
        McpIdentityService service = service(api, store);

        McpIdentityService.LoginView view = service.login("demo", "Demo1234");

        assertEquals("user-1", view.user().id());
        assertEquals("demo", view.user().username());
        assertEquals("token-value", store.readActive(BASE_URL).orElseThrow().token());
    }

    @Test
    void shouldCreateRegistrationLinkWithoutChangingExistingCredential() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        FakePlatformApi api = new FakePlatformApi();
        McpIdentityService service = service(api, store);
        service.login("demo", "Demo1234");

        RegistrationLink link = service.createRegistrationLink();

        assertEquals("link-1", link.id());
        assertEquals("token-value", store.readActive(BASE_URL).orElseThrow().token());
    }

    @Test
    void shouldSaveMcpGeneratedTokenOnlyAfterBrowserLoginCompletes() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        FakePlatformApi api = new FakePlatformApi();
        McpIdentityService service = service(api, store);

        LoginLink link = service.createLoginLink();
        assertFalse(store.readActive(BASE_URL).isPresent());
        assertEquals("login-link-1", link.id());

        api.loginLinkStatus = new LoginLinkStatus("login-link-1", "COMPLETED", "2026-07-18T08:15:00Z",
                "2026-07-19T08:00:00Z", new UserSummary("user-1", "demo", "demo@example.com"));
        McpIdentityService restartedService = service(api, store);
        LoginLinkStatus status = restartedService.loginLinkStatus();

        assertEquals("COMPLETED", status.status());
        assertEquals(link.sessionToken(), store.readActive(BASE_URL).orElseThrow().token());
        assertFalse(store.readPendingLoginLink(BASE_URL).isPresent());
    }

    @Test
    void shouldReadActiveSessionAfterMcpProcessRestarts() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        FakePlatformApi api = new FakePlatformApi();
        McpIdentityService service = service(api, store);
        service.login("demo", "Demo1234");

        McpIdentityService restartedService = service(api, store);
        McpIdentityService.LoginView view = restartedService.currentLogin();

        assertEquals("user-1", view.user().id());
        assertEquals(NOW.plusSeconds(3600), view.expiresAt());
    }

    @Test
    void shouldClearCredentialWhenBackendReturnsUnauthorized() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        FakePlatformApi api = new FakePlatformApi();
        McpIdentityService service = service(api, store);
        service.login("demo", "Demo1234");
        api.currentUserException = new McpIdentityException(McpErrorCode.SESSION_EXPIRED, "登录已失效，请重新登录。");

        McpIdentityException exception = assertThrows(McpIdentityException.class, service::currentUser);

        assertEquals(McpErrorCode.SESSION_EXPIRED, exception.code());
        assertFalse(store.readActive(BASE_URL).isPresent());
    }

    @Test
    void shouldKeepCredentialWhenBackendDeniesAccess() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        FakePlatformApi api = new FakePlatformApi();
        McpIdentityService service = service(api, store);
        service.login("demo", "Demo1234");
        api.currentUserException = new McpIdentityException(McpErrorCode.BACKEND_ACCESS_DENIED, "无权访问。");

        McpIdentityException exception = assertThrows(McpIdentityException.class, service::currentUser);

        assertEquals(McpErrorCode.BACKEND_ACCESS_DENIED, exception.code());
        assertEquals("token-value", store.readActive(BASE_URL).orElseThrow().token());
    }

    @Test
    void shouldClearCredentialWhenLogoutRemoteCallFails() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        FakePlatformApi api = new FakePlatformApi();
        McpIdentityService service = service(api, store);
        service.login("demo", "Demo1234");
        api.logoutException = new McpIdentityException(McpErrorCode.BACKEND_UNAVAILABLE, "服务不可用。");

        service.logout();

        assertFalse(store.readActive(BASE_URL).isPresent());
    }

    private McpIdentityService service(FakePlatformApi api, InMemoryCredentialStore store) {
        return new McpIdentityService(BASE_URL, api, store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static class FakePlatformApi implements PlatformApi {
        private McpIdentityException currentUserException;
        private McpIdentityException logoutException;
        private LoginLinkStatus loginLinkStatus = new LoginLinkStatus("login-link-1", "PENDING", "2026-07-18T08:15:00Z", null, null);

        @Override
        public RegistrationLink createRegistrationLink() {
            return new RegistrationLink("link-1", "http://127.0.0.1:8080/link?token=link-token", "link-token", "2026-07-18T09:00:00Z");
        }

        @Override
        public RegistrationLinkStatus registrationLinkStatus(String id, String token) {
            return new RegistrationLinkStatus(id, "PENDING", "2026-07-18T09:00:00Z", null);
        }

        @Override
        public LoginLink createLoginLink(String sessionTokenHash) {
            return new LoginLink("login-link-1", "http://127.0.0.1:8080/login?token=link-token", "link-token", null,
                    "2026-07-18T08:15:00Z");
        }

        @Override
        public LoginLinkStatus loginLinkStatus(String id, String token) {
            return loginLinkStatus;
        }

        @Override
        public LoginResult login(String login, String password) {
            return new LoginResult("token-value", NOW.plusSeconds(3600), new UserSummary("user-1", "demo", "demo@example.com"));
        }

        @Override
        public UserSummary currentUser(String token) {
            if (currentUserException != null) {
                throw currentUserException;
            }
            return new UserSummary("user-1", "demo", "demo@example.com");
        }

        @Override
        public void logout(String token) {
            if (logoutException != null) {
                throw logoutException;
            }
        }
    }
}

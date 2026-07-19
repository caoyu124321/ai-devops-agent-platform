package devops.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devops.mcp.credential.InMemoryCredentialStore;
import devops.mcp.identity.McpIdentityService;
import devops.mcp.identity.LoginResult;
import devops.mcp.identity.LoginLink;
import devops.mcp.identity.LoginLinkStatus;
import devops.mcp.identity.RegistrationLink;
import devops.mcp.identity.RegistrationLinkStatus;
import devops.mcp.identity.UserSummary;
import devops.mcp.platform.PlatformApi;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class McpToolHandlersTest {
    @Test
    void accountToolsMustNotExposeCredentialsAsInputArguments() {
        McpToolHandlers handlers = new McpToolHandlers(new McpIdentityService("http://127.0.0.1:8080", new NoopPlatformApi(),
                new InMemoryCredentialStore()));

        handlers.tools().stream()
                .filter(tool -> "login_ai_devops".equals(tool.tool().name()) || "register_ai_devops".equals(tool.tool().name()))
                .map(tool -> tool.tool().inputSchema())
                .forEach(inputSchema -> {
                    assertTrue(((Map<?, ?>) inputSchema.get("properties")).isEmpty());
                    assertFalse(inputSchema.toString().contains("password"));
                });
    }

    @Test
    void loginStatusShouldReturnActiveSessionWhenNoPendingLinkExists() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        McpIdentityService identityService = new McpIdentityService("http://127.0.0.1:8080", new NoopPlatformApi(), store);
        identityService.login("demo", "Demo1234");
        McpToolHandlers handlers = new McpToolHandlers(new McpIdentityService("http://127.0.0.1:8080", new NoopPlatformApi(), store));

        Map<String, Object> status = handlers.loginStatus();

        assertEquals("LOGGED_IN", status.get("status"));
        assertEquals("user@example.com", ((Map<?, ?>) status.get("user")).get("email"));
        assertTrue(status.containsKey("expiresAt"));
    }

    @Test
    void capabilityCatalogShouldDescribeEveryRegisteredBusinessToolWithoutSensitiveData() {
        McpToolHandlers handlers = new McpToolHandlers(new McpIdentityService("http://127.0.0.1:8080", new NoopPlatformApi(),
                new InMemoryCredentialStore()));

        Map<String, Object> catalog = handlers.capabilityCatalog();
        List<?> capabilities = (List<?>) catalog.get("capabilities");
        Set<String> registeredTools = handlers.tools().stream().map(tool -> tool.tool().name()).collect(java.util.stream.Collectors.toSet());
        Set<String> catalogTools = capabilities.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(capability -> (String) capability.get("tool"))
                .collect(java.util.stream.Collectors.toSet());

        assertEquals("CAPABILITIES_AVAILABLE", catalog.get("status"));
        assertEquals(registeredTools, catalogTools);
        assertTrue(capabilities.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(capability -> Boolean.TRUE.equals(capability.get("loginRequired"))));
        assertFalse(catalog.toString().toLowerCase().contains("token"));
        assertFalse(catalog.toString().toLowerCase().contains("password"));
    }

    private static class NoopPlatformApi implements PlatformApi {
        @Override
        public RegistrationLink createRegistrationLink() {
            return new RegistrationLink("link", "http://127.0.0.1/link?token=secret", "secret", "2026-07-18T10:00:00Z");
        }

        @Override
        public RegistrationLinkStatus registrationLinkStatus(String id, String token) {
            return new RegistrationLinkStatus(id, "PENDING", "2026-07-18T10:00:00Z", null);
        }

        @Override
        public LoginLink createLoginLink(String sessionTokenHash) {
            return new LoginLink("login-link", "http://127.0.0.1/login?token=secret", "secret", null, "2026-07-18T10:00:00Z");
        }

        @Override
        public LoginLinkStatus loginLinkStatus(String id, String token) {
            return new LoginLinkStatus(id, "PENDING", "2026-07-18T10:00:00Z", null, null);
        }

        @Override
        public LoginResult login(String login, String password) {
            return new LoginResult("token", Instant.now().plusSeconds(3600), new UserSummary("user", "user", "user@example.com"));
        }

        @Override
        public UserSummary currentUser(String token) {
            return new UserSummary("user", "user", "user@example.com");
        }

        @Override
        public void logout(String token) {
        }
    }
}

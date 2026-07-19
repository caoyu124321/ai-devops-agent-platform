package devops.mcp.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import devops.mcp.identity.McpErrorCode;
import devops.mcp.identity.McpIdentityException;
import devops.mcp.identity.LoginLink;
import devops.mcp.identity.LoginLinkStatus;
import devops.mcp.identity.RegistrationLink;
import devops.mcp.identity.RegistrationLinkStatus;
import devops.mcp.identity.UserSummary;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpPlatformApiTest {
    private HttpServer server;
    private HttpPlatformApi api;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        api = new HttpPlatformApi("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldLoginWithoutReturningTokenOutsideInternalResult() {
        server.createContext("/api/v1/auth/login", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            respond(exchange, 200, "{\"token\":\"secret-token\",\"expiresInSeconds\":3600,\"user\":{\"id\":\"user-1\",\"username\":\"demo\",\"email\":\"demo@example.com\"}}");
        });

        var result = api.login("demo", "Demo1234");

        assertEquals("user-1", result.user().id());
        assertEquals("secret-token", result.token());
    }

    @Test
    void shouldCreateRegistrationLinkWithoutSubmittingPassword() {
        server.createContext("/api/v1/auth/registration-links", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(payload.isEmpty());
            respond(exchange, 200, "{\"id\":\"link-2\",\"url\":\"http://127.0.0.1:8080/api/v1/auth/registration-links/link-2/form?token=link-token\",\"expiresAt\":\"2026-07-18T10:00:00Z\"}");
        });

        RegistrationLink result = api.createRegistrationLink();

        assertEquals("link-2", result.id());
        assertEquals("link-token", result.token());
    }

    @Test
    void shouldReadRegistrationLinkStatusWithBearerLinkToken() {
        server.createContext("/api/v1/auth/registration-links/link-2", exchange -> {
            assertEquals("token=link-token", exchange.getRequestURI().getQuery());
            respond(exchange, 200, "{\"id\":\"link-2\",\"status\":\"PENDING\",\"expiresAt\":\"2026-07-18T10:00:00Z\",\"user\":null}");
        });

        RegistrationLinkStatus result = api.registrationLinkStatus("link-2", "link-token");

        assertEquals("PENDING", result.status());
        org.junit.jupiter.api.Assertions.assertNull(result.user());
    }

    @Test
    void shouldMapRegistrationLinkClientErrorToGenericRegistrationFailure() {
        server.createContext("/api/v1/auth/registration-links", exchange -> respond(exchange, 409, "{\"code\":\"LINK_UNAVAILABLE\"}"));

        McpIdentityException exception = assertThrows(McpIdentityException.class,
                api::createRegistrationLink);

        assertEquals(McpErrorCode.REGISTRATION_FAILED, exception.code());
    }

    @Test
    void shouldCreateLoginLinkWithOnlySessionTokenHash() {
        server.createContext("/api/v1/auth/login-links", exchange -> {
            String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(payload.contains("sessionTokenHash"));
            assertTrue(!payload.contains("Demo1234"));
            respond(exchange, 200, "{\"id\":\"login-link\",\"url\":\"http://127.0.0.1:8080/api/v1/auth/login-links/login-link/form?token=link-token\",\"expiresAt\":\"2026-07-18T10:00:00Z\"}");
        });

        LoginLink result = api.createLoginLink("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

        assertEquals("login-link", result.id());
        assertEquals("link-token", result.token());
    }

    @Test
    void shouldReadCompletedLoginLinkWithoutReturningSessionToken() {
        server.createContext("/api/v1/auth/login-links/login-link", exchange -> respond(exchange, 200,
                "{\"id\":\"login-link\",\"status\":\"COMPLETED\",\"expiresAt\":\"2026-07-18T10:00:00Z\",\"sessionExpiresAt\":\"2026-07-19T10:00:00Z\",\"user\":{\"id\":\"user-1\",\"username\":\"demo\",\"email\":\"demo@example.com\"}}"));

        LoginLinkStatus result = api.loginLinkStatus("login-link", "link-token");

        assertEquals("COMPLETED", result.status());
        assertEquals("2026-07-19T10:00:00Z", result.sessionExpiresAt());
        assertEquals("demo", result.user().username());
    }

    @Test
    void shouldAttachBearerTokenWhenReadingCurrentUser() {
        server.createContext("/api/v1/me", exchange -> {
            assertEquals("Bearer stored-token", exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"id\":\"user-1\",\"username\":\"demo\",\"email\":\"demo@example.com\"}");
        });

        UserSummary user = api.currentUser("stored-token");

        assertEquals("demo@example.com", user.email());
    }

    @Test
    void shouldMapUnauthorizedToSessionExpired() {
        server.createContext("/api/v1/me", exchange -> respond(exchange, 401, "{}"));

        McpIdentityException exception = assertThrows(McpIdentityException.class, () -> api.currentUser("stored-token"));

        assertEquals(McpErrorCode.SESSION_EXPIRED, exception.code());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

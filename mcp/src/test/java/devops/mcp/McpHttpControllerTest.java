package devops.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import devops.iam.api.IamException;
import devops.iam.identity.RegistrationLinkService;
import devops.iam.oauth.OAuthProperties;
import devops.iam.oauth.OAuthService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 验证远程 MCP 使用 JSON-RPC 初始化，不依赖旧 stdio 入口。 */
class McpHttpControllerTest {
    @Test
    void initializeShouldReturnStreamableHttpServerMetadata() {
        McpHttpController controller = new McpHttpController(mock(OAuthService.class), mock(RegistrationLinkService.class), new OAuthProperties());

        ResponseEntity<Map<String, Object>> response = controller.handle(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of()), null);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> result = (Map<?, ?>) response.getBody().get("result");
        assertEquals("2025-06-18", result.get("protocolVersion"));
        assertTrue(((Map<?, ?>) result.get("serverInfo")).containsKey("name"));
    }

    @Test
    void loginToolWithoutAccessTokenShouldReturnOAuthAuthenticationChallenge() {
        OAuthProperties properties = new OAuthProperties();
        OAuthService oauthService = mock(OAuthService.class);
        when(oauthService.authenticateAccessToken(null))
                .thenThrow(new IamException("AUTHENTICATION_REQUIRED", HttpStatus.BAD_REQUEST, "需要登录"));
        McpHttpController controller = new McpHttpController(oauthService, mock(RegistrationLinkService.class), properties);

        ResponseEntity<Map<String, Object>> response = controller.handle(
                Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call", "params", Map.of("name", "login_ai_devops")), null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Bearer resource_metadata=\"http://127.0.0.1:8080/.well-known/oauth-protected-resource/mcp\"",
                response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void loginStatusShouldReturnSafeOAuthSummary() {
        OAuthService oauthService = mock(OAuthService.class);
        when(oauthService.authenticateAccessToken("access-token")).thenReturn(new OAuthService.OAuthPrincipal("user-1", "grant-1",
                "client-1", "http://127.0.0.1:8080/mcp", java.util.Set.of("openid", "mcp.tools"),
                Instant.parse("2026-07-25T14:00:00Z"), "demo", "demo@example.com"));
        McpHttpController controller = new McpHttpController(oauthService, mock(RegistrationLinkService.class), new OAuthProperties());

        ResponseEntity<Map<String, Object>> response = controller.handle(
                Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call", "params", Map.of("name", "get_ai_devops_login_status")),
                "Bearer access-token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("LOGGED_IN"));
        assertTrue(response.getBody().toString().contains("demo@example.com"));
        assertFalse(response.getBody().toString().contains("access-token"));
    }
}

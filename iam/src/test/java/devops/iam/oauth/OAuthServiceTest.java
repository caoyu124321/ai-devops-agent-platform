package devops.iam.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import devops.iam.api.IamException;
import devops.iam.dao.OAuthDao;
import devops.iam.identity.IdentityService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 覆盖 OAuth 公共客户端、PKCE 与刷新令牌重用的关键安全边界。 */
class OAuthServiceTest {
    private OAuthDao dao;
    private OAuthService service;

    @BeforeEach
    void setUp() {
        dao = Mockito.mock(OAuthDao.class);
        IdentityService identityService = Mockito.mock(IdentityService.class);
        OAuthProperties properties = new OAuthProperties();
        properties.setIssuer("http://127.0.0.1:8080");
        properties.setAllowLoopbackHttp(true);
        properties.setAutoActivateLoopbackClients(true);
        service = new OAuthService(dao, identityService, new OAuthTokenCodec(), new OidcTokenSigner(properties), properties);
    }

    @Test
    void registerLoopbackClientShouldBeActiveForLocalFinalIntegration() {
        OAuthService.ClientRegistration registration = service.registerClient("Codex", List.of("http://127.0.0.1:3333/callback"), "none",
                List.of("authorization_code", "refresh_token"));

        assertEquals("ACTIVE", registration.status());
        verify(dao).createClient(any(OAuthDao.ClientRow.class));
    }

    @Test
    void registerInternetClientShouldBeActiveByDefaultForPublicAccess() {
        OAuthService.ClientRegistration registration = service.registerClient("External Agent",
                List.of("https://agent.example/callback"), "none", List.of("authorization_code", "refresh_token"));

        assertEquals("ACTIVE", registration.status());
        verify(dao).createClient(any(OAuthDao.ClientRow.class));
    }

    @Test
    void configuredClientIdOverrideShouldActivatePendingClient() {
        OAuthDao.ClientRow pendingClient = new OAuthDao.ClientRow("approved-client", "Unknown", "https://agent.example/callback", "PENDING", Instant.now(), Instant.now());
        when(dao.findClient("approved-client")).thenReturn(Optional.of(pendingClient));
        OAuthProperties properties = new OAuthProperties();
        properties.getClientStatusOverrides().put("approved-client", "ACTIVE");
        OAuthService configuredService = new OAuthService(dao, Mockito.mock(IdentityService.class), new OAuthTokenCodec(), new OidcTokenSigner(properties), properties);

        OAuthService.AuthorizationRequest request = new OAuthService.AuthorizationRequest("code", "approved-client", "https://agent.example/callback",
                "openid mcp.tools", "state", "challenge", "S256", "ai-devops-mcp");

        assertEquals("approved-client", configuredService.validateAuthorization(request).clientId());
    }

    @Test
    void authorizationShouldRejectPlainPkce() {
        OAuthService.AuthorizationRequest request = new OAuthService.AuthorizationRequest("code", "client", "http://127.0.0.1:3333/callback",
                "openid mcp.tools", "state", "challenge", "plain", "ai-devops-mcp");

        assertThrows(IamException.class, () -> service.validateAuthorization(request));
        verify(dao, never()).findClient(any());
    }

    @Test
    void reusedRefreshTokenShouldRevokeEntireGrant() {
        Instant now = Instant.now();
        OAuthDao.RefreshTokenRow rotated = new OAuthDao.RefreshTokenRow("refresh", "hash", "grant", null, "ROTATED", now, now);
        OAuthDao.GrantRow grant = new OAuthDao.GrantRow("grant", "user", "client", "ai-devops-mcp", "mcp.tools offline_access", now.plusSeconds(3600),
                now, null, null, now);
        when(dao.findRefreshTokenForUpdate(any())).thenReturn(Optional.of(rotated));
        when(dao.findGrantForUpdate("grant")).thenReturn(Optional.of(grant));

        assertThrows(IamException.class, () -> service.refresh("client", "old-refresh-token"));

        verify(dao).revokeGrant(eq("grant"), any(), eq("TOKEN_REUSE"));
        verify(dao).revokeGrantTokens(eq("grant"), any(), eq("TOKEN_REUSE"));
    }
}

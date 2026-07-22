package devops.iam.oauth;

import devops.iam.api.IamException;
import devops.iam.dao.OAuthDao;
import devops.iam.identity.IdentityService;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OAuth 核心用例：公共客户端、授权码、Token 轮换和资源认证均在此统一执行。 */
@Service
public class OAuthService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String REFRESH_ACTIVE = "ACTIVE";
    private static final String REASON_TOKEN_REUSE = "TOKEN_REUSE";
    private static final String REASON_TOKEN_REVOKED = "TOKEN_REVOKED";
    private static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    private final OAuthDao dao;
    private final IdentityService identityService;
    private final OAuthTokenCodec codec;
    private final OidcTokenSigner signer;
    private final OAuthProperties properties;

    public OAuthService(OAuthDao dao, IdentityService identityService, OAuthTokenCodec codec, OidcTokenSigner signer,
                        OAuthProperties properties) {
        this.dao = dao;
        this.identityService = identityService;
        this.codec = codec;
        this.signer = signer;
        this.properties = properties;
    }

    @Transactional
    public ClientRegistration registerClient(String clientName, List<String> redirectUris, String tokenEndpointAuthMethod,
                                             List<String> grantTypes) {
        if (clientName == null || clientName.isBlank() || clientName.length() > 128 || redirectUris == null || redirectUris.isEmpty()
                || !"none".equals(tokenEndpointAuthMethod) || !validGrantTypes(grantTypes)) {
            throw bad("INVALID_CLIENT_METADATA", "客户端注册信息不符合要求");
        }
        List<String> normalizedUris = redirectUris.stream().map(this::validateRedirectUri).distinct().toList();
        boolean loopbackOnly = normalizedUris.stream().allMatch(this::isAllowedLoopbackUri);
        String status = properties.isAutoActivateLoopbackClients() && loopbackOnly ? STATUS_ACTIVE : configuredUnknownClientStatus();
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        status = properties.getClientStatusOverrides().getOrDefault(id, status);
        validateClientStatus(status);
        dao.createClient(new OAuthDao.ClientRow(id, clientName.trim(), String.join("\n", normalizedUris), status, now, now));
        return new ClientRegistration(id, status, normalizedUris, List.of(OAuthConstants.GRANT_AUTHORIZATION_CODE,
                OAuthConstants.GRANT_REFRESH_TOKEN));
    }

    public AuthorizationRequest validateAuthorization(AuthorizationRequest request) {
        if (request == null || !"code".equals(request.responseType()) || blank(request.clientId()) || blank(request.redirectUri())
                || blank(request.codeChallenge()) || !OAuthConstants.S256.equals(request.codeChallengeMethod())
                || !OAuthConstants.MCP_AUDIENCE.equals(request.resource())) {
            throw bad("INVALID_AUTHORIZATION_REQUEST", "授权请求不符合要求");
        }
        OAuthDao.ClientRow client = requireActiveClient(request.clientId());
        String redirectUri = validateRedirectUri(request.redirectUri());
        if (!redirectUris(client).contains(redirectUri)) {
            throw bad("INVALID_REDIRECT_URI", "授权请求不符合要求");
        }
        Set<String> scopes = normalizeScopes(request.scope());
        if (!scopes.contains(OAuthConstants.SCOPE_MCP_TOOLS)) {
            throw bad("INVALID_SCOPE", "授权请求不符合要求");
        }
        return new AuthorizationRequest("code", client.id(), redirectUri, canonicalScopes(scopes), request.state(),
                request.codeChallenge(), OAuthConstants.S256, OAuthConstants.MCP_AUDIENCE);
    }

    @Transactional
    public BrowserLogin loginBrowser(String login, String password) {
        IdentityService.UserView user = identityService.authenticateForOAuth(login, password);
        String rawToken = codec.randomValue();
        Instant now = Instant.now();
        dao.createBrowserSession(new OAuthDao.BrowserSessionRow(UUID.randomUUID().toString(), codec.hash(rawToken), user.id(), now,
                now.plus(OAuthConstants.BROWSER_SESSION_TTL)));
        return new BrowserLogin(rawToken, user);
    }

    public IdentityService.UserView authenticateBrowser(String browserToken) {
        if (blank(browserToken)) {
            throw bad("BROWSER_AUTHENTICATION_REQUIRED", "需要登录");
        }
        OAuthDao.BrowserPrincipalRow principal = dao.findBrowserPrincipal(codec.hash(browserToken), Instant.now())
                .orElseThrow(() -> bad("BROWSER_AUTHENTICATION_REQUIRED", "需要登录"));
        return new IdentityService.UserView(principal.userId(), principal.username(), principal.email());
    }

    @Transactional
    public AuthorizationResult approve(String browserToken, AuthorizationRequest request, boolean approved) {
        AuthorizationRequest validated = validateAuthorization(request);
        IdentityService.UserView user = authenticateBrowser(browserToken);
        if (!approved) {
            return AuthorizationResult.denied(redirectWithError(validated.redirectUri(), validated.state()));
        }
        Instant now = Instant.now();
        String grantId = UUID.randomUUID().toString();
        dao.createGrant(new OAuthDao.GrantRow(grantId, user.id(), validated.clientId(), validated.resource(), validated.scope(),
                now.plus(OAuthConstants.REFRESH_GRANT_TTL), now, null, null, now));
        String rawCode = codec.randomValue();
        dao.createAuthorizationCode(new OAuthDao.AuthorizationCodeRow(UUID.randomUUID().toString(), codec.hash(rawCode), grantId,
                validated.clientId(), validated.redirectUri(), validated.codeChallenge(), now.plus(OAuthConstants.AUTHORIZATION_CODE_TTL), null,
                now));
        return AuthorizationResult.approved(appendQuery(validated.redirectUri(), "code", rawCode, "state", validated.state()));
    }

    @Transactional
    public TokenResponse exchangeAuthorizationCode(String clientId, String code, String redirectUri, String verifier) {
        if (blank(clientId) || blank(code) || blank(redirectUri) || blank(verifier)) {
            throw bad("INVALID_GRANT", "授权码无效");
        }
        OAuthDao.AuthorizationCodeRow authorizationCode = dao.findAuthorizationCodeForUpdate(codec.hash(code))
                .orElseThrow(() -> bad("INVALID_GRANT", "授权码无效"));
        Instant now = Instant.now();
        if (authorizationCode.consumedAt() != null || !authorizationCode.expiresAt().isAfter(now)
                || !authorizationCode.clientId().equals(clientId) || !authorizationCode.redirectUri().equals(redirectUri)
                || !constantTimeEquals(authorizationCode.codeChallenge(), codec.s256(verifier))) {
            throw bad("INVALID_GRANT", "授权码无效");
        }
        OAuthDao.GrantRow grant = requireUsableGrant(authorizationCode.grantId(), clientId, now);
        if (!dao.consumeAuthorizationCode(authorizationCode.id(), now)) {
            throw bad("INVALID_GRANT", "授权码无效");
        }
        return issueTokens(grant, null, now, true);
    }

    @Transactional
    public TokenResponse refresh(String clientId, String refreshToken) {
        if (blank(clientId) || blank(refreshToken)) {
            throw bad("INVALID_GRANT", "刷新令牌无效");
        }
        OAuthDao.RefreshTokenRow current = dao.findRefreshTokenForUpdate(codec.hash(refreshToken))
                .orElseThrow(() -> bad("INVALID_GRANT", "刷新令牌无效"));
        Instant now = Instant.now();
        OAuthDao.GrantRow grant = dao.findGrantForUpdate(current.grantId()).orElse(null);
        if (grant == null || !grant.clientId().equals(clientId) || !REFRESH_ACTIVE.equals(current.status())) {
            if (grant != null) revokeGrant(grant.id(), now, REASON_TOKEN_REUSE);
            throw bad("INVALID_GRANT", "刷新令牌无效");
        }
        if (grant.revokedAt() != null || !grant.absoluteExpiresAt().isAfter(now)
                || !grant.lastUsedAt().plus(OAuthConstants.REFRESH_IDLE_TTL).isAfter(now)) {
            revokeGrant(grant.id(), now, REASON_TOKEN_REVOKED);
            throw bad("INVALID_GRANT", "刷新令牌无效");
        }
        if (!dao.rotateRefreshToken(current.id(), now)) {
            revokeGrant(grant.id(), now, REASON_TOKEN_REUSE);
            throw bad("INVALID_GRANT", "刷新令牌无效");
        }
        dao.updateGrantUsage(grant.id(), now);
        return issueTokens(grant, current.id(), now, false);
    }

    public OAuthPrincipal authenticateAccessToken(String rawToken) {
        if (blank(rawToken)) {
            throw bad("AUTHENTICATION_REQUIRED", "需要登录");
        }
        OAuthDao.AccessPrincipalRow row = dao.findAccessPrincipal(codec.hash(rawToken), Instant.now())
                .orElseThrow(() -> bad("AUTHENTICATION_REQUIRED", "需要登录"));
        Set<String> scopes = splitScopes(row.scopes());
        if (!OAuthConstants.MCP_AUDIENCE.equals(row.audience()) || !scopes.contains(OAuthConstants.SCOPE_MCP_TOOLS)) {
            throw bad("AUTHENTICATION_REQUIRED", "需要登录");
        }
        return new OAuthPrincipal(row.userId(), row.grantId(), row.clientId(), row.audience(), scopes, row.expiresAt(), row.username(), row.email());
    }

    @Transactional
    public void revokeByAccessToken(String rawToken) {
        if (blank(rawToken)) return;
        dao.findAccessPrincipal(codec.hash(rawToken), Instant.now()).ifPresent(principal -> revokeGrant(principal.grantId(), Instant.now(), REASON_TOKEN_REVOKED));
    }

    @Transactional
    public void revokeUserAfterPasswordChange(String userId) {
        dao.revokeUser(userId, Instant.now(), REASON_PASSWORD_CHANGED);
    }

    public MapMetadata metadata() {
        String issuer = properties.getIssuer();
        return new MapMetadata(issuer, issuer + "/oauth/authorize", issuer + "/oauth/token", issuer + "/oauth/userinfo", issuer + "/oauth/jwks",
                issuer + "/oauth/register");
    }

    public java.util.Map<String, Object> jwks() { return java.util.Map.of("keys", List.of(signer.jwk())); }

    private TokenResponse issueTokens(OAuthDao.GrantRow grant, String parentRefreshTokenId, Instant now, boolean includeIdToken) {
        String accessToken = codec.randomValue();
        Instant expiresAt = now.plus(OAuthConstants.ACCESS_TOKEN_TTL);
        dao.createAccessToken(new OAuthDao.AccessTokenRow(UUID.randomUUID().toString(), codec.hash(accessToken), grant.id(), grant.userId(),
                grant.clientId(), grant.audience(), grant.scopes(), now, expiresAt, null, null));
        String refreshToken = null;
        if (splitScopes(grant.scopes()).contains(OAuthConstants.SCOPE_OFFLINE_ACCESS)) {
            refreshToken = codec.randomValue();
            dao.createRefreshToken(new OAuthDao.RefreshTokenRow(UUID.randomUUID().toString(), codec.hash(refreshToken), grant.id(),
                    parentRefreshTokenId, REFRESH_ACTIVE, now, null));
        }
        String idToken = includeIdToken && splitScopes(grant.scopes()).contains(OAuthConstants.SCOPE_OPENID)
                ? signer.sign(grant.userId(), grant.clientId(), now) : null;
        return new TokenResponse(accessToken, OAuthConstants.TOKEN_TYPE_BEARER, OAuthConstants.ACCESS_TOKEN_TTL.toSeconds(), grant.scopes(), refreshToken, idToken);
    }

    private OAuthDao.ClientRow requireActiveClient(String clientId) {
        OAuthDao.ClientRow client = dao.findClient(clientId).orElseThrow(() -> bad("INVALID_CLIENT", "客户端不可用"));
        if (!STATUS_ACTIVE.equals(effectiveClientStatus(client))) {
            throw bad("INVALID_CLIENT", "客户端不可用");
        }
        return client;
    }

    private OAuthDao.GrantRow requireUsableGrant(String grantId, String clientId, Instant now) {
        OAuthDao.GrantRow grant = dao.findGrantForUpdate(grantId).orElseThrow(() -> bad("INVALID_GRANT", "授权码无效"));
        if (!grant.clientId().equals(clientId) || grant.revokedAt() != null || !grant.absoluteExpiresAt().isAfter(now)) {
            throw bad("INVALID_GRANT", "授权码无效");
        }
        requireActiveClient(clientId);
        return grant;
    }

    private void revokeGrant(String grantId, Instant now, String reason) {
        dao.revokeGrant(grantId, now, reason);
        dao.revokeGrantTokens(grantId, now, reason);
    }

    /**
     * 客户端名称可由注册请求自行填写，不能作为可信品牌或授权准入依据。
     * 对外开放模式下未知公共客户端可默认启用，但显式停用始终具有最高优先级。
     */
    private String effectiveClientStatus(OAuthDao.ClientRow client) {
        if (STATUS_SUSPENDED.equals(client.status())) return STATUS_SUSPENDED;
        String override = properties.getClientStatusOverrides().get(client.id());
        if (override == null || override.isBlank()) return client.status();
        validateClientStatus(override);
        return override;
    }

    private String configuredUnknownClientStatus() {
        String status = properties.getUnknownClientDefaultStatus();
        validateClientStatus(status);
        return status;
    }

    private void validateClientStatus(String status) {
        if (!STATUS_ACTIVE.equals(status) && !STATUS_PENDING.equals(status) && !STATUS_SUSPENDED.equals(status)) {
            throw new IllegalStateException("OAuth 客户端状态配置无效");
        }
    }

    private String validateRedirectUri(String candidate) {
        try {
            URI uri = new URI(candidate);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (!uri.isAbsolute() || uri.getFragment() != null || uri.getUserInfo() != null || (https && uri.getHost() == null)) {
                throw bad("INVALID_REDIRECT_URI", "回调地址不符合要求");
            }
            if (!https && !(properties.isAllowLoopbackHttp() && isAllowedLoopbackUri(candidate))) {
                throw bad("INVALID_REDIRECT_URI", "回调地址不符合要求");
            }
            return uri.normalize().toString();
        } catch (URISyntaxException exception) {
            throw bad("INVALID_REDIRECT_URI", "回调地址不符合要求");
        }
    }

    private boolean isAllowedLoopbackUri(String candidate) {
        try {
            URI uri = new URI(candidate);
            return "http".equalsIgnoreCase(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private Set<String> normalizeScopes(String scope) {
        Set<String> scopes = splitScopes(scope);
        if (scopes.isEmpty() || !OAuthConstants.SUPPORTED_SCOPES.containsAll(scopes)) {
            throw bad("INVALID_SCOPE", "授权请求不符合要求");
        }
        return scopes;
    }

    private Set<String> splitScopes(String scope) {
        if (scope == null) return Set.of();
        return Arrays.stream(scope.trim().split("\\s+")).filter(item -> !item.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String canonicalScopes(Set<String> scopes) { return scopes.stream().sorted().collect(Collectors.joining(" ")); }

    private boolean validGrantTypes(List<String> grantTypes) {
        return grantTypes != null && !grantTypes.isEmpty() && grantTypes.stream().allMatch(type -> OAuthConstants.GRANT_AUTHORIZATION_CODE.equals(type)
                || OAuthConstants.GRANT_REFRESH_TOKEN.equals(type)) && grantTypes.contains(OAuthConstants.GRANT_AUTHORIZATION_CODE);
    }

    private List<String> redirectUris(OAuthDao.ClientRow client) { return List.of(client.redirectUris().split("\\R")); }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(java.nio.charset.StandardCharsets.US_ASCII), right.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private String appendQuery(String base, String... pairs) {
        StringBuilder value = new StringBuilder(base);
        value.append(base.contains("?") ? '&' : '?');
        for (int index = 0; index < pairs.length; index += 2) {
            if (index > 0) value.append('&');
            value.append(java.net.URLEncoder.encode(pairs[index], java.nio.charset.StandardCharsets.UTF_8));
            value.append('=');
            value.append(java.net.URLEncoder.encode(pairs[index + 1] == null ? "" : pairs[index + 1], java.nio.charset.StandardCharsets.UTF_8));
        }
        return value.toString();
    }

    private String redirectWithError(String redirectUri, String state) { return appendQuery(redirectUri, "error", "access_denied", "state", state); }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private IamException bad(String code, String message) { return new IamException(code, HttpStatus.BAD_REQUEST, message); }

    public record ClientRegistration(String clientId, String status, List<String> redirectUris, List<String> grantTypes) { }
    public record AuthorizationRequest(String responseType, String clientId, String redirectUri, String scope, String state,
                                       String codeChallenge, String codeChallengeMethod, String resource) { }
    public record BrowserLogin(String browserToken, IdentityService.UserView user) { }
    public record AuthorizationResult(boolean approved, String redirectUrl) {
        static AuthorizationResult approved(String redirectUrl) { return new AuthorizationResult(true, redirectUrl); }
        static AuthorizationResult denied(String redirectUrl) { return new AuthorizationResult(false, redirectUrl); }
    }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String scope, String refreshToken, String idToken) { }
    public record OAuthPrincipal(String userId, String grantId, String clientId, String audience, Set<String> scopes,
                                 Instant expiresAt, String username, String email) { }
    public record MapMetadata(String issuer, String authorizationEndpoint, String tokenEndpoint, String userinfoEndpoint,
                              String jwksUri, String registrationEndpoint) { }
}

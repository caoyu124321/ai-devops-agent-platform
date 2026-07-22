package devops.iam.oauth;

import devops.iam.api.IamException;
import devops.iam.identity.IdentityService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** OAuth Controller 仅完成 HTTP、浏览器跳转和 Cookie 协议转换，令牌与授权规则全部委托 OAuthService。 */
@RestController
class OAuthController {
    private static final String BROWSER_COOKIE = "AI_DEVOPS_OAUTH_SESSION";
    private final OAuthService service;
    private final OAuthProperties properties;
    private final OAuthClientRegistrationRateLimiter registrationRateLimiter;

    OAuthController(OAuthService service, OAuthProperties properties, OAuthClientRegistrationRateLimiter registrationRateLimiter) {
        this.service = service;
        this.properties = properties;
        this.registrationRateLimiter = registrationRateLimiter;
    }

    @GetMapping("/.well-known/openid-configuration")
    Map<String, Object> openIdConfiguration() {
        OAuthService.MapMetadata metadata = service.metadata();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuer", metadata.issuer());
        body.put("authorization_endpoint", metadata.authorizationEndpoint());
        body.put("token_endpoint", metadata.tokenEndpoint());
        body.put("userinfo_endpoint", metadata.userinfoEndpoint());
        body.put("jwks_uri", metadata.jwksUri());
        body.put("registration_endpoint", metadata.registrationEndpoint());
        body.put("response_types_supported", List.of("code"));
        body.put("grant_types_supported", List.of(OAuthConstants.GRANT_AUTHORIZATION_CODE, OAuthConstants.GRANT_REFRESH_TOKEN));
        body.put("code_challenge_methods_supported", List.of(OAuthConstants.S256));
        body.put("scopes_supported", OAuthConstants.SUPPORTED_SCOPES);
        body.put("id_token_signing_alg_values_supported", List.of("EdDSA"));
        return body;
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    Map<String, Object> authorizationServerMetadata() {
        OAuthService.MapMetadata metadata = service.metadata();
        return Map.of("issuer", metadata.issuer(), "authorization_endpoint", metadata.authorizationEndpoint(), "token_endpoint", metadata.tokenEndpoint(),
                "registration_endpoint", metadata.registrationEndpoint(), "grant_types_supported", List.of(OAuthConstants.GRANT_AUTHORIZATION_CODE,
                        OAuthConstants.GRANT_REFRESH_TOKEN), "code_challenge_methods_supported", List.of(OAuthConstants.S256),
                "scopes_supported", OAuthConstants.SUPPORTED_SCOPES);
    }

    @GetMapping("/.well-known/oauth-protected-resource/mcp")
    Map<String, Object> protectedResourceMetadata() {
        return Map.of("resource", properties.getIssuer() + "/mcp", "authorization_servers", List.of(properties.getIssuer()),
                "scopes_supported", List.of(OAuthConstants.SCOPE_MCP_TOOLS));
    }

    @GetMapping("/oauth/jwks")
    Map<String, Object> jwks() { return service.jwks(); }

    @PostMapping("/oauth/register")
    ResponseEntity<Map<String, Object>> register(@RequestBody ClientRegistrationRequest request, HttpServletRequest servletRequest) {
        registrationRateLimiter.check(servletRequest.getRemoteAddr());
        OAuthService.ClientRegistration registration = service.registerClient(request.clientName(), request.redirectUris(), request.tokenEndpointAuthMethod(), request.grantTypes());
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(Map.of("client_id", registration.clientId(),
                "status", registration.status(), "redirect_uris", registration.redirectUris(), "grant_types", registration.grantTypes(),
                "token_endpoint_auth_method", "none"));
    }

    @GetMapping("/oauth/authorize")
    ResponseEntity<String> authorize(@RequestParam Map<String, String> parameters,
                                     @CookieValue(value = BROWSER_COOKIE, required = false) String browserToken) {
        OAuthService.AuthorizationRequest request = service.validateAuthorization(toAuthorizationRequest(parameters));
        try {
            IdentityService.UserView user = service.authenticateBrowser(browserToken);
            return html(consentPage(request, user));
        } catch (IamException exception) {
            if (!"BROWSER_AUTHENTICATION_REQUIRED".equals(exception.code())) throw exception;
            return html(loginPage(request));
        }
    }

    @PostMapping("/oauth/authorize/login")
    ResponseEntity<Void> login(@RequestParam Map<String, String> parameters) {
        OAuthService.AuthorizationRequest request = service.validateAuthorization(toAuthorizationRequest(parameters));
        OAuthService.BrowserLogin login = service.loginBrowser(parameters.get("login"), parameters.get("password"));
        ResponseCookie cookie = ResponseCookie.from(BROWSER_COOKIE, login.browserToken()).httpOnly(true).secure(!properties.isAllowLoopbackHttp())
                .sameSite("Lax").path("/").maxAge(OAuthConstants.BROWSER_SESSION_TTL).build();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(authorizationUrl(request))).build();
    }

    @PostMapping("/oauth/authorize/consent")
    ResponseEntity<Void> consent(@RequestParam Map<String, String> parameters,
                                 @CookieValue(value = BROWSER_COOKIE, required = false) String browserToken) {
        OAuthService.AuthorizationResult result = service.approve(browserToken, toAuthorizationRequest(parameters), "approve".equals(parameters.get("decision")));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.redirectUrl())).build();
    }

    @PostMapping("/oauth/token")
    ResponseEntity<Map<String, Object>> token(@RequestParam Map<String, String> parameters) {
        OAuthService.TokenResponse response;
        String grantType = parameters.get("grant_type");
        if (OAuthConstants.GRANT_AUTHORIZATION_CODE.equals(grantType)) {
            response = service.exchangeAuthorizationCode(parameters.get("client_id"), parameters.get("code"), parameters.get("redirect_uri"),
                    parameters.get("code_verifier"));
        } else if (OAuthConstants.GRANT_REFRESH_TOKEN.equals(grantType)) {
            response = service.refresh(parameters.get("client_id"), parameters.get("refresh_token"));
        } else {
            throw new IamException("UNSUPPORTED_GRANT_TYPE", HttpStatus.BAD_REQUEST, "不支持的授权类型");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", response.accessToken());
        body.put("token_type", response.tokenType());
        body.put("expires_in", response.expiresIn());
        body.put("scope", response.scope());
        if (response.refreshToken() != null) body.put("refresh_token", response.refreshToken());
        if (response.idToken() != null) body.put("id_token", response.idToken());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header("Pragma", "no-cache").body(body);
    }

    @PostMapping("/oauth/revoke")
    ResponseEntity<Void> revoke(@RequestParam(value = "token", required = false) String token) {
        service.revokeByAccessToken(token);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/oauth/introspect")
    Map<String, Object> introspect(@RequestParam(value = "token", required = false) String token, HttpServletRequest request) {
        if (!isLoopbackRequest(request)) {
            throw new IamException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "没有执行此操作的权限");
        }
        try {
            OAuthService.OAuthPrincipal principal = service.authenticateAccessToken(token);
            return Map.of("active", true, "sub", principal.userId(), "client_id", principal.clientId(), "aud", principal.audience(),
                    "scope", String.join(" ", principal.scopes()), "exp", principal.expiresAt().getEpochSecond());
        } catch (IamException exception) {
            return Map.of("active", false);
        }
    }

    @GetMapping("/oauth/userinfo")
    Map<String, Object> userinfo(@RequestHeader(value = "Authorization", required = false) String authorization) {
        OAuthService.OAuthPrincipal principal = service.authenticateAccessToken(bearerToken(authorization));
        if (!principal.scopes().contains(OAuthConstants.SCOPE_OPENID)) {
            throw new IamException("INSUFFICIENT_SCOPE", HttpStatus.FORBIDDEN, "没有执行此操作的权限");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sub", principal.userId());
        if (principal.scopes().contains(OAuthConstants.SCOPE_PROFILE)) body.put("preferred_username", principal.username());
        if (principal.scopes().contains(OAuthConstants.SCOPE_EMAIL)) body.put("email", principal.email());
        return body;
    }

    private OAuthService.AuthorizationRequest toAuthorizationRequest(Map<String, String> parameters) {
        return new OAuthService.AuthorizationRequest(parameters.get("response_type"), parameters.get("client_id"), parameters.get("redirect_uri"),
                parameters.get("scope"), parameters.get("state"), parameters.get("code_challenge"), parameters.get("code_challenge_method"),
                parameters.get("resource"));
    }

    private ResponseEntity<String> html(String body) { return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8").body(body); }

    private String loginPage(OAuthService.AuthorizationRequest request) {
        return "<html><body><h1>登录 AI DevOps</h1><form method='post' action='/oauth/authorize/login'>" + hiddenInputs(request)
                + "<label>用户名或邮箱 <input name='login' required></label><br><label>密码 <input type='password' name='password' required></label><br>"
                + "<button type='submit'>登录</button></form></body></html>";
    }

    private String consentPage(OAuthService.AuthorizationRequest request, IdentityService.UserView user) {
        return "<html><body><h1>授权 AI DevOps Agent</h1><p>当前用户：" + escape(user.username()) + "</p><p>请求权限：" + escape(request.scope())
                + "</p><form method='post' action='/oauth/authorize/consent'>" + hiddenInputs(request)
                + "<button name='decision' value='approve' type='submit'>同意</button><button name='decision' value='deny' type='submit'>拒绝</button></form></body></html>";
    }

    private String hiddenInputs(OAuthService.AuthorizationRequest request) {
        List<String> values = new ArrayList<>();
        values.add(hidden("response_type", request.responseType()));
        values.add(hidden("client_id", request.clientId()));
        values.add(hidden("redirect_uri", request.redirectUri()));
        values.add(hidden("scope", request.scope()));
        values.add(hidden("state", request.state()));
        values.add(hidden("code_challenge", request.codeChallenge()));
        values.add(hidden("code_challenge_method", request.codeChallengeMethod()));
        values.add(hidden("resource", request.resource()));
        return String.join("", values);
    }

    private String hidden(String name, String value) { return "<input type='hidden' name='" + escape(name) + "' value='" + escape(value) + "'>"; }

    private String authorizationUrl(OAuthService.AuthorizationRequest request) {
        return UriComponentsBuilder.fromPath("/oauth/authorize").queryParam("response_type", request.responseType())
                .queryParam("client_id", request.clientId()).queryParam("redirect_uri", request.redirectUri()).queryParam("scope", request.scope())
                .queryParam("state", request.state()).queryParam("code_challenge", request.codeChallenge())
                .queryParam("code_challenge_method", request.codeChallengeMethod()).queryParam("resource", request.resource()).build().encode().toUriString();
    }

    private boolean isLoopbackRequest(HttpServletRequest request) {
        return "127.0.0.1".equals(request.getRemoteAddr()) || "0:0:0:0:0:0:0:1".equals(request.getRemoteAddr());
    }

    private String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    record ClientRegistrationRequest(@JsonProperty("client_name") String clientName,
                                   @JsonProperty("redirect_uris") List<String> redirectUris,
                                   @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod,
                                   @JsonProperty("grant_types") List<String> grantTypes) { }
}

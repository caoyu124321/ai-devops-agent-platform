package devops;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import devops.iam.oauth.OAuthService;
import devops.iam.identity.IdentityService;
import devops.iam.tenant.TenantService;
import devops.projectmanagement.environment.EnvironmentTarget;
import devops.projectmanagement.service.CredentialService;
import devops.projectmanagement.service.EnvironmentService;
import devops.projectmanagement.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证唯一主应用能够启动并装配基础 HTTP 能力。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "app.project-management.crypto.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureTestRestTemplate
class AiDevopsAgentPlatformApplicationTests {
    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Autowired
    private OAuthService oauthService;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private EnvironmentService environmentService;

    @Test
    void contextLoads() {
    }

    @Test
    void heartbeatReturnsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/heartbeat", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).contains("\"timestamp\"");
    }

    @Test
    void localH2SupportsRegistrationOAuthLoginAndMcpLoginStatus() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<java.util.Map<String, Object>> request = new HttpEntity<>(java.util.Map.of(
                "client_name", "application-integration-test",
                "redirect_uris", java.util.List.of("http://127.0.0.1:49123/callback"),
                "token_endpoint_auth_method", "none",
                "grant_types", java.util.List.of("authorization_code")), headers);

        ResponseEntity<JsonNode> clientResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/oauth/register", request, JsonNode.class);
        assertThat(clientResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(clientResponse.getBody().path("status").asText()).isEqualTo("ACTIVE");

        ResponseEntity<JsonNode> linkResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/registration-links", null, JsonNode.class);
        URI linkUrl = URI.create(linkResponse.getBody().path("url").asText());
        String registrationId = linkUrl.getPath().split("/")[5];
        String registrationToken = UriComponentsBuilder.fromUri(linkUrl).build().getQueryParams().getFirst("token");
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> registrationForm = new LinkedMultiValueMap<>();
        registrationForm.add("username", "oauth-demo");
        registrationForm.add("email", "oauth-demo@example.com");
        registrationForm.add("password", "OAuthPass123");
        ResponseEntity<String> registrationResponse = restTemplate.postForEntity("http://localhost:" + port
                + "/api/v1/auth/registration-links/" + registrationId + "/complete?token=" + registrationToken,
                new HttpEntity<>(registrationForm, formHeaders), String.class);
        assertThat(registrationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String verifier = "codex-test-pkce-verifier-012345678901234567890123456789012345";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String redirectUri = "http://127.0.0.1:49123/callback";
        OAuthService.AuthorizationRequest authorizationRequest = new OAuthService.AuthorizationRequest("code",
                clientResponse.getBody().path("client_id").asText(), redirectUri, "openid profile mcp.tools", "codex-test-state", challenge,
                "S256", "http://127.0.0.1:8080/mcp");
        OAuthService.BrowserLogin browserLogin = oauthService.loginBrowser("oauth-demo", "OAuthPass123");
        OAuthService.AuthorizationResult consent = oauthService.approve(browserLogin.browserToken(), authorizationRequest, true);
        String authorizationCode = UriComponentsBuilder.fromUri(URI.create(consent.redirectUrl())).build().getQueryParams().getFirst("code");
        assertThat(authorizationCode).isNotBlank();

        MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
        tokenForm.add("grant_type", "authorization_code");
        tokenForm.add("client_id", clientResponse.getBody().path("client_id").asText());
        tokenForm.add("redirect_uri", redirectUri);
        tokenForm.add("code", authorizationCode);
        tokenForm.add("code_verifier", verifier);
        ResponseEntity<JsonNode> tokenResponse = restTemplate.postForEntity("http://localhost:" + port + "/oauth/token",
                new HttpEntity<>(tokenForm, formHeaders), JsonNode.class);
        assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders mcpHeaders = new HttpHeaders();
        mcpHeaders.setContentType(MediaType.APPLICATION_JSON);
        mcpHeaders.setBearerAuth(tokenResponse.getBody().path("access_token").asText());
        ResponseEntity<String> statusResponse = restTemplate.postForEntity("http://localhost:" + port + "/mcp", new HttpEntity<>(java.util.Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "tools/call", "params", java.util.Map.of("name", "get_ai_devops_login_status")), mcpHeaders),
                String.class);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).contains("LOGGED_IN").contains("oauth-demo@example.com");
    }

    @Test
    void localH2SupportsAuthenticatedProjectCreation() {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        IdentityService.UserView user = identityService.register("project-" + suffix, "project-" + suffix + "@example.com", "ProjectPass123");
        String tenantId = tenantService.create(user.id(), "tenant-" + suffix).id();
        String token = identityService.login("project-" + suffix, "ProjectPass123", "integration-test").token();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/tenants/"
                + tenantId + "/projects", new HttpEntity<>(java.util.Map.of("name", "project-" + suffix, "description", "integration"), headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("tenantId").asText()).isEqualTo(tenantId);
        assertThat(response.getBody().path("version").asInt()).isEqualTo(1);
    }

    @Test
    void localH2PersistsEncryptedCredentialGrantAndKubernetesEnvironmentVersion() {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        IdentityService.UserView user = identityService.register("environment-" + suffix,
                "environment-" + suffix + "@example.com", "EnvironmentPass123");
        String tenantId = tenantService.create(user.id(), "environment-tenant-" + suffix).id();
        ProjectService.ProjectView project = projectService.create(user.id(), tenantId, "environment-project-" + suffix, null);
        CredentialService.CredentialView credential = credentialService.create(user.id(), tenantId, "kube-" + suffix,
                "KUBECONFIG", java.util.Map.of("kubeconfig", "apiVersion: v1"));
        credentialService.grant(user.id(), credential.id(), project.id());

        EnvironmentService.EnvironmentView environment = environmentService.create(user.id(), project.id(), "dev-" + suffix,
                "DEV", credential.id(), new EnvironmentTarget.KubernetesTarget("https://127.0.0.1:1", null, "app", java.util.List.of("app")));

        assertThat(environment.version()).isEqualTo(1);
        assertThat(environment.targetType()).isEqualTo("KUBERNETES");
        assertThat(environment.connectionStatus()).isEqualTo("UNAVAILABLE");
    }

}

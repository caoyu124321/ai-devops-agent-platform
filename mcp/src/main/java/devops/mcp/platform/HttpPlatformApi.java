package devops.mcp.platform;

import devops.mcp.identity.LoginResult;
import devops.mcp.identity.LoginLink;
import devops.mcp.identity.LoginLinkStatus;
import devops.mcp.identity.McpErrorCode;
import devops.mcp.identity.McpIdentityException;
import devops.mcp.identity.RegistrationLink;
import devops.mcp.identity.RegistrationLinkStatus;
import devops.mcp.identity.UserSummary;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 以最小 HTTP 边界访问平台 IAM REST API，并将外部错误转换为 MCP 安全错误码。 */
public class HttpPlatformApi implements PlatformApi {
    private static final String API_PREFIX = "/api/v1";
    private static final String REGISTRATION_LINK_PATH = "/auth/registration-links";
    private static final String LOGIN_LINK_PATH = "/auth/login-links";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String CURRENT_USER_PATH = "/me";
    private static final String LOGOUT_PATH = "/auth/logout";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_AGENT = "ai-devops-mcp/0.0.1";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final URI baseUri;
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public HttpPlatformApi(String baseUrl) {
        this(validateBaseUri(baseUrl), HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), new ObjectMapper());
    }

    HttpPlatformApi(URI baseUri, HttpClient client, ObjectMapper objectMapper) {
        this.baseUri = baseUri;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public RegistrationLink createRegistrationLink() {
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint(REGISTRATION_LINK_PATH))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            RegistrationLinkResponse body = readJson(response.body(), RegistrationLinkResponse.class);
            return new RegistrationLink(body.id(), body.url(), tokenFromUrl(body.url()), body.expiresAt());
        }
        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            throw new McpIdentityException(McpErrorCode.REGISTRATION_FAILED, "注册失败，请检查注册信息后重试。");
        }
        throw backendUnavailable();
    }

    @Override
    public RegistrationLinkStatus registrationLinkStatus(String id, String token) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint(REGISTRATION_LINK_PATH + "/" + id + "?token=" + token))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            RegistrationLinkStatusResponse body = readJson(response.body(), RegistrationLinkStatusResponse.class);
            return new RegistrationLinkStatus(body.id(), body.status(), body.expiresAt(), body.user());
        }
        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            throw new McpIdentityException(McpErrorCode.REGISTRATION_FAILED, "注册链接不可用或已失效。");
        }
        throw backendUnavailable();
    }

    @Override
    public LoginLink createLoginLink(String sessionTokenHash) {
        String payload = writeJson(new CreateLoginLinkRequest(sessionTokenHash));
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint(LOGIN_LINK_PATH))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LoginLinkResponse body = readJson(response.body(), LoginLinkResponse.class);
            return new LoginLink(body.id(), body.url(), tokenFromUrl(body.url()), null, body.expiresAt());
        }
        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            throw new McpIdentityException(McpErrorCode.LOGIN_FAILED, "无法创建安全登录链接。");
        }
        throw backendUnavailable();
    }

    @Override
    public LoginLinkStatus loginLinkStatus(String id, String token) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint(LOGIN_LINK_PATH + "/" + id + "?token=" + token))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LoginLinkStatusResponse body = readJson(response.body(), LoginLinkStatusResponse.class);
            return new LoginLinkStatus(body.id(), body.status(), body.expiresAt(), body.sessionExpiresAt(), body.user());
        }
        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            throw new McpIdentityException(McpErrorCode.LOGIN_FAILED, "登录链接不可用或已失效。");
        }
        throw backendUnavailable();
    }

    @Override
    public LoginResult login(String login, String password) {
        String payload = writeJson(new LoginRequest(login, password));
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint(LOGIN_PATH))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LoginResponse loginResponse = readJson(response.body(), LoginResponse.class);
            return new LoginResult(loginResponse.token(), Instant.now().plusSeconds(loginResponse.expiresInSeconds()), loginResponse.user());
        }
        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            throw new McpIdentityException(McpErrorCode.LOGIN_FAILED, "登录失败，请检查用户名或密码。");
        }
        throw backendUnavailable();
    }

    @Override
    public UserSummary currentUser(String token) {
        HttpResponse<String> response = send(protectedRequest(CURRENT_USER_PATH, "GET", token));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return readJson(response.body(), UserSummary.class);
        }
        throw protectedApiError(response.statusCode());
    }

    @Override
    public void logout(String token) {
        HttpResponse<String> response = send(protectedRequest(LOGOUT_PATH, "POST", token));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw protectedApiError(response.statusCode());
    }

    private HttpRequest protectedRequest(String path, String method, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(path))
                .timeout(REQUEST_TIMEOUT)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .header("User-Agent", USER_AGENT);
        if ("GET".equals(method)) {
            return builder.GET().build();
        }
        return builder.POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw backendUnavailable(exception);
        } catch (IOException exception) {
            throw backendUnavailable(exception);
        }
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new McpIdentityException(McpErrorCode.BACKEND_UNAVAILABLE, "无法构造平台请求。", exception);
        }
    }

    private <T> T readJson(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JacksonException exception) {
            throw new McpIdentityException(McpErrorCode.BACKEND_UNAVAILABLE, "平台响应格式异常。", exception);
        }
    }

    private McpIdentityException protectedApiError(int statusCode) {
        if (statusCode == 401) {
            return new McpIdentityException(McpErrorCode.SESSION_EXPIRED, "登录已失效，请重新登录。");
        }
        if (statusCode == 403 || statusCode == 404) {
            return new McpIdentityException(McpErrorCode.BACKEND_ACCESS_DENIED, "当前账号无权访问该资源。");
        }
        return backendUnavailable();
    }

    private McpIdentityException backendUnavailable() {
        return new McpIdentityException(McpErrorCode.BACKEND_UNAVAILABLE, "AI DevOps 服务暂时不可用，请稍后重试。");
    }

    private McpIdentityException backendUnavailable(Throwable cause) {
        return new McpIdentityException(McpErrorCode.BACKEND_UNAVAILABLE, "AI DevOps 服务暂时不可用，请稍后重试。", cause);
    }

    private URI endpoint(String path) {
        return baseUri.resolve(API_PREFIX + path);
    }

    private String tokenFromUrl(String url) {
        String query = URI.create(url).getRawQuery();
        if (query == null || !query.startsWith("token=")) {
            throw new McpIdentityException(McpErrorCode.BACKEND_UNAVAILABLE, "平台注册链接格式异常。");
        }
        return query.substring("token=".length());
    }

    private static URI validateBaseUri(String baseUrl) {
        URI uri = URI.create(baseUrl);
        String host = uri.getHost();
        boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("AI DevOps 服务地址必须使用 HTTP 或 HTTPS。");
        }
        if (!loopback) {
            throw new IllegalArgumentException("本期 MCP 仅允许访问本机 AI DevOps 服务。");
        }
        return uri.toString().endsWith("/") ? uri : URI.create(uri + "/");
    }

    private record RegistrationLinkResponse(String id, String url, String expiresAt) {
    }

    private record RegistrationLinkStatusResponse(String id, String status, String expiresAt, UserSummary user) {
    }

    private record CreateLoginLinkRequest(String sessionTokenHash) {
    }

    private record LoginLinkResponse(String id, String url, String expiresAt) {
    }

    private record LoginLinkStatusResponse(String id, String status, String expiresAt, String sessionExpiresAt,
                                           UserSummary user) {
    }

    private record LoginRequest(String login, String password) {
    }

    private record LoginResponse(String token, long expiresInSeconds, UserSummary user) {
    }
}

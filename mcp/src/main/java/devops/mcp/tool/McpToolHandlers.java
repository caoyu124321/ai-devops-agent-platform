package devops.mcp.tool;

import devops.mcp.identity.LoginLink;
import devops.mcp.identity.LoginLinkStatus;
import devops.mcp.identity.McpIdentityException;
import devops.mcp.identity.McpIdentityService;
import devops.mcp.identity.RegistrationLink;
import devops.mcp.identity.RegistrationLinkStatus;
import devops.mcp.identity.UserSummary;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 将 MCP 协议细节隔离在工具层，并保证输出中不包含密码或 Token。 */
public class McpToolHandlers {
    private static final String CAPABILITIES_TOOL = "get_ai_devops_capabilities";
    private static final String REGISTER_TOOL = "register_ai_devops";
    private static final String REGISTRATION_STATUS_TOOL = "get_ai_devops_registration_status";
    private static final String LOGIN_TOOL = "login_ai_devops";
    private static final String LOGIN_STATUS_TOOL = "get_ai_devops_login_status";
    private static final String CURRENT_USER_TOOL = "get_current_ai_devops_user";
    private static final String LOGOUT_TOOL = "logout_ai_devops";
    private static final String STATUS_CAPABILITIES_AVAILABLE = "CAPABILITIES_AVAILABLE";
    private static final String STATUS_REGISTRATION_LINK_CREATED = "REGISTRATION_LINK_CREATED";
    private static final String STATUS_LOGIN_LINK_CREATED = "LOGIN_LINK_CREATED";
    private static final String STATUS_LOGGED_IN = "LOGGED_IN";
    private static final String STATUS_LOGGED_OUT = "LOGGED_OUT";
    private static final String STATUS_ERROR = "ERROR";
    private static final Map<String, Object> EMPTY_INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false);
    private static final List<Capability> CAPABILITIES = List.of(
            new Capability("capability-discovery", "能力查询", "查询当前 AI DevOps 已支持能力与使用方式。", CAPABILITIES_TOOL, false,
                    List.of("AI DevOps 支持哪些能力", "ai-d 能做什么")),
            new Capability("capability-discovery", "能力查询", "查询当前 AI DevOps 已支持能力与使用方式。", CAPABILITIES_TOOL, false,
                    List.of("AI DevOps 支持哪些能力", "ai-d 能做什么")),
            new Capability("register", "注册", "创建安全注册链接，在本机浏览器中完成账号注册。", REGISTER_TOOL, false,
                    List.of("注册 ai-devops", "创建 AI DevOps 账号")),
            new Capability("registration-status", "查询注册状态", "查询当前注册链接是否已完成注册。", REGISTRATION_STATUS_TOOL, false,
                    List.of("查询注册状态", "我的 AI DevOps 注册完成了吗")),
            new Capability("login", "登录", "创建安全登录链接，在本机浏览器中完成登录。", LOGIN_TOOL, false,
                    List.of("登录 ai-devops", "登录 ai-d")),
            new Capability("login-status", "查询登录状态", "查询当前登录链接或本地会话状态。", LOGIN_STATUS_TOOL, false,
                    List.of("我登录了吗", "查询 ai-d 登录状态")),
            new Capability("current-user", "当前用户", "读取当前已登录用户的安全摘要。", CURRENT_USER_TOOL, true,
                    List.of("当前 AI DevOps 用户是谁", "查看我的登录用户")),
            new Capability("logout", "登出", "登出 AI DevOps 并清理本机登录凭据。", LOGOUT_TOOL, true,
                    List.of("登出 ai-devops", "退出 ai-d")));

    private final McpIdentityService identityService;
    private final ObjectMapper objectMapper;

    public McpToolHandlers(McpIdentityService identityService) {
        this(identityService, new ObjectMapper());
    }

    McpToolHandlers(McpIdentityService identityService, ObjectMapper objectMapper) {
        this.identityService = identityService;
        this.objectMapper = objectMapper;
    }

    public List<McpServerFeatures.SyncToolSpecification> tools() {
        return List.of(capabilitiesTool(), registerTool(), registrationStatusTool(), loginTool(), loginStatusTool(), currentUserTool(),
                logoutTool());
    }

    private McpServerFeatures.SyncToolSpecification capabilitiesTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder(CAPABILITIES_TOOL, EMPTY_INPUT_SCHEMA)
                        .description("查询当前 AI DevOps 已支持能力。适用于“AI DevOps 能做什么”“ai-d 支持什么”或意图不明确的请求。")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).idempotentHint(true).build())
                        .build())
                .callHandler((exchange, request) -> execute(this::capabilityCatalog))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification registerTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder(REGISTER_TOOL, EMPTY_INPUT_SCHEMA)
                        .description("创建本机 AI DevOps 安全注册链接。请在返回的浏览器页面设置密码；密码不会发送给 Codex。")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false).idempotentHint(false).build())
                        .build())
                .callHandler((exchange, request) -> execute(() -> registrationLink(identityService.createRegistrationLink())))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification registrationStatusTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder(REGISTRATION_STATUS_TOOL, EMPTY_INPUT_SCHEMA)
                        .description("查询当前 AI DevOps 安全注册链接的完成状态。")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).idempotentHint(true).build())
                        .build())
                .callHandler((exchange, request) -> execute(() -> registrationStatus(identityService.registrationLinkStatus())))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification loginTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder(LOGIN_TOOL, EMPTY_INPUT_SCHEMA)
                        .description("创建本机 AI DevOps 安全登录链接。请在浏览器页面输入用户名和密码；密码不会发送给 Codex。")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false).idempotentHint(false).build())
                        .build())
                .callHandler((exchange, request) -> execute(() -> loginLink(identityService.createLoginLink())))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification loginStatusTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder(LOGIN_STATUS_TOOL, EMPTY_INPUT_SCHEMA)
                        .description("查询当前 AI DevOps 登录状态；优先查询待完成链接，完成后保存本机会话，否则返回已保存会话。")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false).idempotentHint(true).build())
                        .build())
                .callHandler((exchange, request) -> execute(this::loginStatus))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification currentUserTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder(CURRENT_USER_TOOL, EMPTY_INPUT_SCHEMA)
                        .description("查询当前 AI DevOps 登录用户。")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).idempotentHint(true).build())
                        .build())
                .callHandler((exchange, request) -> execute(() -> loggedIn(identityService.currentUser(), null)))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification logoutTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder(LOGOUT_TOOL, EMPTY_INPUT_SCHEMA)
                        .description("登出本机 AI DevOps 平台，并清理本地登录凭据。")
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false).idempotentHint(true).build())
                        .build())
                .callHandler((exchange, request) -> execute(this::logout))
                .build();
    }

    private Map<String, Object> loggedIn(UserSummary user, String expiresAt) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("status", STATUS_LOGGED_IN);
        content.put("user", safeUser(user));
        if (expiresAt != null) {
            content.put("expiresAt", expiresAt);
        }
        return content;
    }

    /** 能力目录仅描述已注册工具，不触发后端请求或访问用户凭据。 */
    Map<String, Object> capabilityCatalog() {
        return Map.of(
                "status", STATUS_CAPABILITIES_AVAILABLE,
                "capabilities", CAPABILITIES.stream().map(Capability::toResponse).toList());
    }

    private Map<String, Object> registrationLink(RegistrationLink link) {
        return Map.of(
                "status", STATUS_REGISTRATION_LINK_CREATED,
                "registrationLinkId", link.id(),
                "url", link.url(),
                "expiresAt", link.expiresAt(),
                "message", "请在本机浏览器打开链接完成注册，密码不会发送给 Codex。");
    }

    private Map<String, Object> loginLink(LoginLink link) {
        return Map.of(
                "status", STATUS_LOGIN_LINK_CREATED,
                "loginLinkId", link.id(),
                "url", link.url(),
                "expiresAt", link.expiresAt(),
                "message", "请在本机浏览器打开链接完成登录，密码不会发送给 Codex。");
    }

    private Map<String, Object> loginStatus(LoginLinkStatus link) {
        if ("COMPLETED".equals(link.status()) && link.user() != null && link.sessionExpiresAt() != null) {
            return loggedIn(link.user(), link.sessionExpiresAt());
        }
        return Map.of("status", link.status(), "loginLinkId", link.id(), "expiresAt", link.expiresAt());
    }

    /**
     * 登录链接在完成后会被清理；此时回退读取活动会话，避免 stdio MCP 重启后把有效登录误报为未创建链接。
     */
    Map<String, Object> loginStatus() {
        try {
            return loginStatus(identityService.loginLinkStatus());
        } catch (McpIdentityException exception) {
            if (exception.code() != devops.mcp.identity.McpErrorCode.LOGIN_LINK_NOT_FOUND) {
                throw exception;
            }
            McpIdentityService.LoginView session = identityService.currentLogin();
            return loggedIn(session.user(), session.expiresAt().toString());
        }
    }

    private Map<String, Object> registrationStatus(RegistrationLinkStatus link) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("status", link.status());
        content.put("registrationLinkId", link.id());
        content.put("expiresAt", link.expiresAt());
        if (link.user() != null) {
            content.put("user", safeUser(link.user()));
        }
        return content;
    }

    private Map<String, String> safeUser(UserSummary user) {
        return Map.of("id", user.id(), "username", user.username(), "email", user.email());
    }

    private Map<String, Object> logout() {
        identityService.logout();
        return Map.of("status", STATUS_LOGGED_OUT);
    }

    private McpSchema.CallToolResult execute(Supplier<Map<String, Object>> action) {
        try {
            return success(action.get());
        } catch (McpIdentityException exception) {
            return error(exception);
        }
    }

    private McpSchema.CallToolResult success(Map<String, Object> content) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(writeJson(content))
                .structuredContent(content)
                .build();
    }

    private McpSchema.CallToolResult error(McpIdentityException exception) {
        Map<String, Object> content = Map.of(
                "status", STATUS_ERROR,
                "code", exception.code().name(),
                "message", exception.getMessage());
        return McpSchema.CallToolResult.builder()
                .addTextContent(writeJson(content))
                .structuredContent(content)
                .isError(true)
                .build();
    }

    private String writeJson(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JacksonException exception) {
            return "{\"status\":\"ERROR\",\"code\":\"BACKEND_UNAVAILABLE\",\"message\":\"无法生成安全响应。\"}";
        }
    }

    private record Capability(String id, String name, String description, String tool, boolean loginRequired, List<String> examples) {
        private Map<String, Object> toResponse() {
            return Map.of(
                    "id", id,
                    "name", name,
                    "description", description,
                    "tool", tool,
                    "loginRequired", loginRequired,
                    "examples", examples);
        }
    }
}

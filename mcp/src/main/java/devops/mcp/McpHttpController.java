package devops.mcp;

import devops.iam.api.IamException;
import devops.iam.identity.RegistrationLinkService;
import devops.iam.oauth.OAuthProperties;
import devops.iam.oauth.OAuthService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Streamable HTTP MCP 的无状态 JSON-RPC 适配器；所有受保护工具都复用 IAM OAuth 主体。 */
@RestController
@RequestMapping("/mcp")
class McpHttpController {
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String MCP_PROTOCOL_VERSION = "2025-06-18";
    private final OAuthService oauthService;
    private final RegistrationLinkService registrationLinkService;
    private final OAuthProperties properties;

    McpHttpController(OAuthService oauthService, RegistrationLinkService registrationLinkService, OAuthProperties properties) {
        this.oauthService = oauthService;
        this.registrationLinkService = registrationLinkService;
        this.properties = properties;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> handle(@RequestBody Map<String, Object> request,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        String method = stringValue(request.get("method"));
        Object id = request.get("id");
        if ("notifications/initialized".equals(method)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        try {
            Map<String, Object> result = dispatch(method, mapValue(request.get("params")), bearerToken(authorization));
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(Map.of("jsonrpc", JSON_RPC_VERSION, "id", id, "result", result));
        } catch (IamException exception) {
            if ("AUTHENTICATION_REQUIRED".equals(exception.code())) {
                return oauthAuthenticationChallenge();
            }
            return rejectedRequest(id);
        } catch (RuntimeException exception) {
            return rejectedRequest(id);
        }
    }

    /**
     * 受保护工具缺少或携带无效令牌时，必须以 HTTP 认证挑战通知 Agent 发现 OAuth 流程，不能伪装成普通 MCP 成功响应。
     */
    private ResponseEntity<Map<String, Object>> oauthAuthenticationChallenge() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer resource_metadata=\"" + protectedResourceMetadataUrl() + "\"")
                .build();
    }

    private ResponseEntity<Map<String, Object>> rejectedRequest(Object id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(Map.of("jsonrpc", JSON_RPC_VERSION, "id", id,
                "error", Map.of("code", -32001, "message", "请求被拒绝，请登录或检查权限。")));
    }

    private Map<String, Object> dispatch(String method, Map<String, Object> parameters, String accessToken) {
        if ("initialize".equals(method)) {
            return Map.of("protocolVersion", MCP_PROTOCOL_VERSION, "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "ai-devops", "version", "1.0.0"),
                    "instructions", "AI DevOps、ai-devops、ai devops 与 ai-d 均指本服务。可先调用 get_ai_devops_capabilities 查询能力。");
        }
        if ("tools/list".equals(method)) return Map.of("tools", tools());
        if ("tools/call".equals(method)) return callTool(stringValue(parameters.get("name")), accessToken);
        throw new IllegalArgumentException("不支持的 MCP 方法");
    }

    private Map<String, Object> callTool(String name, String accessToken) {
        if ("get_ai_devops_capabilities".equals(name)) {
            return textResult("当前支持：注册、OAuth 浏览器登录、查询登录状态、查询当前用户、登出。需要登录的业务工具会自动使用 OAuth 身份。");
        }
        if ("register_ai_devops".equals(name)) {
            RegistrationLinkService.LinkCreation link = registrationLinkService.create(properties.getIssuer());
            return textResult("注册链接：" + link.url() + "，过期时间：" + link.expiresAt());
        }
        OAuthService.OAuthPrincipal principal = oauthService.authenticateAccessToken(accessToken);
        if ("login_ai_devops".equals(name)) {
            return loginStatusResult(principal);
        }
        if ("get_ai_devops_login_status".equals(name)) {
            return loginStatusResult(principal);
        }
        if ("get_current_ai_devops_user".equals(name)) {
            return textResult("当前用户：" + principal.username() + "（" + principal.email() + "）");
        }
        if ("logout_ai_devops".equals(name)) {
            oauthService.revokeByAccessToken(accessToken);
            return textResult("已撤销当前 Agent 的 AI DevOps 登录授权。");
        }
        throw new IllegalArgumentException("不支持的 MCP 工具");
    }

    private List<Map<String, Object>> tools() {
        return List.of(tool("get_ai_devops_capabilities", "查询当前 AI DevOps MCP 能力。"),
                tool("register_ai_devops", "取得安全注册链接；密码只在浏览器页面输入。"),
                tool("login_ai_devops", "登录 AI DevOps；未认证时触发浏览器 OAuth 授权。"),
                tool("get_ai_devops_login_status", "查询当前 AI DevOps OAuth 登录状态。"),
                tool("get_current_ai_devops_user", "查询当前 OAuth 登录用户。"),
                tool("logout_ai_devops", "撤销当前 Agent 的 OAuth 授权链。"));
    }

    private String protectedResourceMetadataUrl() {
        return properties.getMcpProtectedResourceMetadataUrl();
    }

    private Map<String, Object> tool(String name, String description) {
        return Map.of("name", name, "description", description, "inputSchema", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false));
    }

    private Map<String, Object> textResult(String text) { return Map.of("content", List.of(Map.of("type", "text", "text", text))); }

    /** 登录状态只返回 OAuth 主体摘要和过期时间，防止工具输出泄露访问令牌。 */
    private Map<String, Object> loginStatusResult(OAuthService.OAuthPrincipal principal) {
        return textResult("登录状态：LOGGED_IN，当前用户：" + principal.username() + "（" + principal.email()
                + "），会话失效时间：" + principal.expiresAt());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }

    private String stringValue(Object value) { return value instanceof String string ? string : ""; }

    private String bearerToken(String authorization) { return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null; }
}

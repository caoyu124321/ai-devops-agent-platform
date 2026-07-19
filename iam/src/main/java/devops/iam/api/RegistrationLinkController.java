package devops.iam.api;

import devops.iam.identity.IdentityService;
import devops.iam.identity.RegistrationLinkService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 注册链接 REST 协议层；密码字段仅从浏览器表单转发给领域服务，绝不进入 MCP 工具。 */
@RestController
@RequestMapping("/api/v1/auth/registration-links")
class RegistrationLinkController {
    private final RegistrationLinkService service;

    RegistrationLinkController(RegistrationLinkService service) {
        this.service = service;
    }

    @PostMapping
    RegistrationLinkService.LinkCreation create(HttpServletRequest request) {
        return service.create(localBaseUrl(request));
    }

    @GetMapping("/{id}")
    RegistrationLinkService.LinkView status(@PathVariable String id, @RequestParam String token) {
        return service.status(id, token);
    }

    @GetMapping(value = "/{id}/form", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> form(@PathVariable String id, @RequestParam String token) {
        service.requirePending(id, token);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(formHtml(id, token, null));
    }

    @PostMapping(value = "/{id}/complete", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> complete(@PathVariable String id, @RequestParam String token, @RequestParam String username,
                                    @RequestParam String email, @RequestParam String password) {
        IdentityService.UserView user = service.complete(id, token, username, email, password);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(successHtml(user));
    }

    private String localBaseUrl(HttpServletRequest request) {
        return URI.create("http://127.0.0.1:" + request.getLocalPort()).toString();
    }

    private String formHtml(String id, String token, String message) {
        String safeId = escape(id);
        String safeToken = escape(token);
        String feedback = message == null ? "" : "<p role=\"alert\">" + escape(message) + "</p>";
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>注册 AI DevOps</title>"
                + "<h1>注册 AI DevOps</h1>" + feedback
                + "<form method=\"post\" action=\"/api/v1/auth/registration-links/" + safeId + "/complete\">"
                + "<input type=\"hidden\" name=\"token\" value=\"" + safeToken + "\">"
                + "<label>用户名 <input name=\"username\" required maxlength=\"64\"></label><br>"
                + "<label>邮箱 <input name=\"email\" type=\"email\" required maxlength=\"254\"></label><br>"
                + "<label>密码 <input name=\"password\" type=\"password\" required maxlength=\"128\" autocomplete=\"new-password\"></label>"
                + "<p>密码至少 8 位，且同时包含字母和数字。</p><button type=\"submit\">安全注册</button></form></html>";
    }

    private String successHtml(IdentityService.UserView user) {
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>注册完成</title>"
                + "<h1>注册完成</h1><p>账号 " + escape(user.username()) + " 已创建，可返回 Codex 登录。</p></html>";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

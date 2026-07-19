package devops.iam.api;

import devops.iam.identity.IdentityService;
import devops.iam.identity.LoginLinkService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 登录链接控制器只做浏览器协议转换，密码校验与会话创建均由 IAM 服务完成。 */
@RestController
@RequestMapping("/api/v1/auth/login-links")
class LoginLinkController {
    private final LoginLinkService service;

    LoginLinkController(LoginLinkService service) {
        this.service = service;
    }

    @PostMapping
    LoginLinkService.LinkCreation create(@RequestBody CreateLoginLinkRequest body, HttpServletRequest request) {
        return service.create(localBaseUrl(request), body.sessionTokenHash());
    }

    @GetMapping("/{id}")
    LoginLinkService.LinkView status(@PathVariable String id, @RequestParam String token) {
        return service.status(id, token);
    }

    @GetMapping(value = "/{id}/form", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> form(@PathVariable String id, @RequestParam String token) {
        service.requirePending(id, token);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(formHtml(id, token));
    }

    @PostMapping(value = "/{id}/complete", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> complete(@PathVariable String id, @RequestParam String token, @RequestParam String login,
                                    @RequestParam String password) {
        IdentityService.SessionLoginView session = service.complete(id, token, login, password);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(successHtml(session.user()));
    }

    private String localBaseUrl(HttpServletRequest request) {
        return URI.create("http://127.0.0.1:" + request.getLocalPort()).toString();
    }

    private String formHtml(String id, String token) {
        String safeId = escape(id);
        String safeToken = escape(token);
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>登录 AI DevOps</title>"
                + "<h1>登录 AI DevOps</h1><form method=\"post\" action=\"/api/v1/auth/login-links/" + safeId + "/complete\">"
                + "<input type=\"hidden\" name=\"token\" value=\"" + safeToken + "\">"
                + "<label>用户名或邮箱 <input name=\"login\" required maxlength=\"254\" autocomplete=\"username\"></label><br>"
                + "<label>密码 <input name=\"password\" type=\"password\" required maxlength=\"128\" autocomplete=\"current-password\"></label><br>"
                + "<button type=\"submit\">安全登录</button></form></html>";
    }

    private String successHtml(IdentityService.UserView user) {
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>登录完成</title>"
                + "<h1>登录完成</h1><p>账号 " + escape(user.username()) + " 已验证，可返回 Codex 完成登录。</p></html>";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    record CreateLoginLinkRequest(String sessionTokenHash) {
    }
}

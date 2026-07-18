package devops.iam.api;

import devops.iam.identity.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 身份 REST 接口只负责协议转换，业务规则统一由 IdentityService 执行。 */
@RestController
@RequestMapping("/api/v1")
class AuthController {
    private final IdentityService service;

    AuthController(IdentityService service) {
        this.service = service;
    }

    @PostMapping("/auth/register")
    IdentityService.UserView register(@RequestBody RegisterRequest body) {
        return service.register(body.username(), body.email(), body.password());
    }

    @PostMapping("/auth/login")
    IdentityService.LoginView login(@RequestBody LoginRequest body, HttpServletRequest request) {
        return service.login(body.login(), body.password(), request.getHeader("User-Agent"));
    }

    @PostMapping("/auth/logout")
    Map<String, Object> logout(HttpServletRequest request) {
        service.logout(principal(request).sessionId());
        return Map.of();
    }

    @PostMapping("/auth/password/change")
    Map<String, Object> changePassword(@RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        service.changePassword(principal(request).user().id(), body.oldPassword(), body.newPassword());
        return Map.of();
    }

    @GetMapping("/me")
    IdentityService.UserView me(HttpServletRequest request) {
        return principal(request).user();
    }

    private IdentityService.SessionPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute("iamPrincipal");
        if (value instanceof IdentityService.SessionPrincipal principal) {
            return principal;
        }
        throw new IamException("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "需要登录");
    }

    record RegisterRequest(String username, String email, String password) {
    }

    record LoginRequest(String login, String password) {
    }

    record ChangePasswordRequest(String oldPassword, String newPassword) {
    }
}

package devops.iam.api;

import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationDecision;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationScope;
import devops.iam.contract.AuthorizationService;
import devops.iam.identity.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 为后续业务模块提供统一授权预检入口，控制器只负责请求契约转换。 */
@RestController
@RequestMapping("/api/v1/authorization")
class AuthorizationController {
    private final AuthorizationService authorizationService;

    AuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @PostMapping("/check")
    AuthorizationDecision check(@RequestBody CheckRequest body, HttpServletRequest request) {
        IdentityService.SessionPrincipal principal = principal(request);
        validate(body);
        AuthorizationScope scope = new AuthorizationScope(parseScopeType(body.scopeType()), body.tenantId(),
                body.projectId(), body.environmentId(), parseEnvironmentLevel(body.environmentLevel()));
        return authorizationService.authorize(new AuthorizationRequest(
                new AuthenticatedSubject(principal.user().id(), principal.sessionId(), Instant.now()),
                body.resourceType(), body.resourceId(), body.actionCode(), scope,
                body.context() == null ? Map.of() : body.context()));
    }

    private void validate(CheckRequest body) {
        if (body == null || blank(body.resourceType()) || blank(body.resourceId()) || blank(body.actionCode())
                || blank(body.scopeType())) {
            throw new IamException("AUTHORIZATION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "授权请求不完整");
        }
    }

    private AuthorizationScope.ScopeType parseScopeType(String scopeType) {
        try {
            return AuthorizationScope.ScopeType.valueOf(scopeType);
        } catch (IllegalArgumentException exception) {
            throw new IamException("AUTHORIZATION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "授权范围不受支持");
        }
    }

    private AuthorizationScope.EnvironmentLevel parseEnvironmentLevel(String environmentLevel) {
        if (environmentLevel == null || environmentLevel.isBlank()) {
            return null;
        }
        try {
            return AuthorizationScope.EnvironmentLevel.valueOf(environmentLevel);
        } catch (IllegalArgumentException exception) {
            throw new IamException("AUTHORIZATION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "环境等级不受支持");
        }
    }

    private IdentityService.SessionPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute("iamPrincipal");
        if (value instanceof IdentityService.SessionPrincipal principal) {
            return principal;
        }
        throw new IamException("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "需要登录");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    record CheckRequest(String resourceType, String resourceId, String actionCode, String scopeType,
                        String tenantId, String projectId, String environmentId, String environmentLevel,
                        Map<String, String> context) { }
}

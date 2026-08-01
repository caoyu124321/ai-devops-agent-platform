package devops.pipeline.api;

import devops.iam.identity.IdentityService;
import devops.iam.oauth.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 兼容 IAM 会话与 OAuth 访问令牌，流水线接口始终只向服务层传递当前用户标识。 */
@Component
class PipelineCurrentActorResolver {
    String requireActorId(HttpServletRequest request) {
        Object principal = request.getAttribute("iamPrincipal");
        if (principal instanceof IdentityService.SessionPrincipal session) {
            return session.user().id();
        }
        if (principal instanceof OAuthService.OAuthPrincipal oauth) {
            return oauth.userId();
        }
        throw new PipelineException("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "需要登录后才能访问流水线");
    }
}

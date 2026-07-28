package devops.projectmanagement.api;

import devops.iam.identity.IdentityService;
import devops.iam.oauth.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 将 IAM 已认证的会话或 OAuth 主体转换为业务服务需要的用户标识，Controller 不自行解析 Token。 */
@Component
public class CurrentActorResolver {
    public String requireActorId(HttpServletRequest request) {
        Object principal = request.getAttribute("iamPrincipal");
        if (principal instanceof IdentityService.SessionPrincipal sessionPrincipal) {
            return sessionPrincipal.user().id();
        }
        if (principal instanceof OAuthService.OAuthPrincipal oauthPrincipal) {
            return oauthPrincipal.userId();
        }
        throw new ProjectManagementException("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "需要登录");
    }
}

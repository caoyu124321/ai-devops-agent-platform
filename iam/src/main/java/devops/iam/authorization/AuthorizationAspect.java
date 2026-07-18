package devops.iam.authorization;

import devops.iam.api.IamException;
import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationScope;
import devops.iam.contract.AuthorizationService;
import devops.iam.identity.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 将标注方法统一接入授权内核，避免后续模块直接比较角色编码。 */
@Aspect
@Component
public class AuthorizationAspect {
    private final AuthorizationService authorizationService;
    public AuthorizationAspect(AuthorizationService authorizationService) { this.authorizationService = authorizationService; }
    @Around("@annotation(rule)")
    public Object authorize(ProceedingJoinPoint point, RequireAuthorization rule) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        Object value = request.getAttribute("iamPrincipal");
        if (!(value instanceof IdentityService.SessionPrincipal principal)) throw new IamException("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "需要登录");
        Object[] args = point.getArgs();
        String tenantId = String.valueOf(args[rule.tenantIdArgument()]);
        String resourceId = rule.resourceIdArgument() < 0 ? tenantId : String.valueOf(args[rule.resourceIdArgument()]);
        authorizationService.requireAuthorization(new AuthorizationRequest(new AuthenticatedSubject(principal.user().id(), principal.sessionId(), Instant.now()), rule.resourceType(), resourceId, rule.action(), new AuthorizationScope(AuthorizationScope.ScopeType.TENANT, tenantId, null, null, null), Map.of()));
        return point.proceed();
    }
}

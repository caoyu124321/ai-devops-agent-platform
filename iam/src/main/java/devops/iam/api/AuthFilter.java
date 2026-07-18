package devops.iam.api;

import devops.iam.identity.IdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 将 Bearer Token 转换为请求主体；无效 Token 直接返回统一且不含敏感信息的错误响应。 */
@Component
class AuthFilter extends OncePerRequestFilter {
    private final IdentityService identityService;

    AuthFilter(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                request.setAttribute("iamPrincipal", identityService.authenticate(header.substring(7)));
            } catch (IamException exception) {
                response.setStatus(exception.status().value());
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"" + exception.code()
                        + "\",\"message\":\"需要登录\",\"traceId\":\"" + UUID.randomUUID()
                        + "\",\"details\":{}}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

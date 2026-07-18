package devops.iam.api;

import devops.iam.identity.IdentityService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 无效 Token 必须得到统一认证错误，避免过滤器异常成为 500。 */
class AuthFilterTest {
    @Test
    void shouldReturnUnauthorizedJsonForInvalidToken() throws Exception {
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.authenticate("invalid-token"))
                .thenThrow(new IamException("AUTHENTICATION_REQUIRED", org.springframework.http.HttpStatus.UNAUTHORIZED, "需要登录"));
        AuthFilter filter = new AuthFilter(identityService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(response.getContentAsString().contains("AUTHENTICATION_REQUIRED"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getContentAsString().contains("traceId"));
        verify(chain, org.mockito.Mockito.never()).doFilter(request, response);
    }
}

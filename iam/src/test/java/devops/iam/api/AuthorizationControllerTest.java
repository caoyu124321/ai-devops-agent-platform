package devops.iam.api;

import devops.iam.contract.AuthorizationService;
import devops.iam.identity.IdentityService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 非法授权请求必须在 REST 契约层转换为业务错误，而不是泄漏枚举解析异常。 */
class AuthorizationControllerTest {
    @Test
    void shouldRejectUnsupportedScopeTypeBeforeAuthorizationService() {
        AuthorizationService service = mock(AuthorizationService.class);
        AuthorizationController controller = new AuthorizationController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("iamPrincipal", new IdentityService.SessionPrincipal("session-1",
                new IdentityService.UserView("user-1", "user", "user@example.com")));

        IamException exception = assertThrows(IamException.class, () -> controller.check(
                new AuthorizationController.CheckRequest("PROJECT", "project-1", "pipeline.run", "UNKNOWN",
                        "tenant-1", "project-1", null, null, Map.of()), request));

        assertEquals("AUTHORIZATION_REQUEST_INVALID", exception.code());
        verifyNoInteractions(service);
    }
}

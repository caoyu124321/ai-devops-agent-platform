package devops.iam.api;

import devops.iam.identity.LoginLinkService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 登录页面只能渲染密码控件，不能将密码值或会话令牌写入页面。 */
class LoginLinkControllerTest {
    @Test
    void shouldRenderBrowserPasswordInputWithoutPasswordValue() {
        LoginLinkService service = mock(LoginLinkService.class);
        LoginLinkController controller = new LoginLinkController(service);

        String page = controller.form("login-link", "holder-token").getBody();

        assertTrue(page.contains("type=\"password\""));
        assertFalse(page.contains("Demo1234"));
        verify(service).requirePending("login-link", "holder-token");
    }
}

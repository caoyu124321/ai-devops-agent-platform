package devops.iam.api;

import devops.iam.identity.RegistrationLinkService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 浏览器表单只提供密码控件，不把任何密码值预填进 HTML。 */
class RegistrationLinkControllerTest {
    @Test
    void shouldRenderPasswordFieldWithoutPuttingPasswordInPage() {
        RegistrationLinkService service = mock(RegistrationLinkService.class);
        RegistrationLinkController controller = new RegistrationLinkController(service);

        String page = controller.form("link-1", "holder-token").getBody();

        assertTrue(page.contains("type=\"password\""));
        assertFalse(page.contains("Demo1234"));
        verify(service).requirePending("link-1", "holder-token");
    }
}

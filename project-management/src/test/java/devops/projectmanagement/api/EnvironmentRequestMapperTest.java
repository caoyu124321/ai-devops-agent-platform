package devops.projectmanagement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import devops.projectmanagement.environment.EnvironmentTarget;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnvironmentRequestMapperTest {
    private final EnvironmentRequestMapper mapper = new EnvironmentRequestMapper();

    @Test
    void mapsKubernetesRequestWithoutMixingControllerBusinessLogic() {
        EnvironmentTarget target = mapper.target(new EnvironmentController.EnvironmentRequest("dev", "DEV", "credential",
                "KUBERNETES", "https://cluster", "context", "app", List.of("app"), null, 0, null, null, null));
        assertThat(target).isInstanceOf(EnvironmentTarget.KubernetesTarget.class);
    }

    @Test
    void rejectsUnknownTargetType() {
        assertThatThrownBy(() -> mapper.target(new EnvironmentController.EnvironmentRequest("dev", "DEV", "credential",
                "UNKNOWN", null, null, null, List.of(), null, 0, null, null, null)))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(error -> ((ProjectManagementException) error).code())
                .isEqualTo("TARGET_CONFIGURATION_INVALID");
    }
}

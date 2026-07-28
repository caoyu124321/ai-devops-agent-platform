package devops.projectmanagement.environment;

import static org.assertj.core.api.Assertions.assertThat;

import devops.projectmanagement.domain.ConnectionStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadOnlyEnvironmentConnectionValidatorTest {
    private final ReadOnlyEnvironmentConnectionValidator validator = new ReadOnlyEnvironmentConnectionValidator();

    @Test
    void rejectsUnreachableKubernetesEndpointWithoutReportingHealthy() {
        EnvironmentConnectionValidator.ValidationResult result = validator.validate(
                new EnvironmentTarget.KubernetesTarget("https://127.0.0.1:1", null, "app", List.of("app")), "credential");
        assertThat(result.status()).isEqualTo(ConnectionStatus.UNAVAILABLE);
        assertThat(result.errorCode()).isEqualTo("KUBERNETES_UNAVAILABLE");
    }

    @Test
    void rejectsUnreachableSshHostWithoutBypassingFingerprintVerification() {
        EnvironmentConnectionValidator.ValidationResult result = validator.validate(
                new EnvironmentTarget.LinuxHostTarget("127.0.0.1", 1, "SHA256:invalid"), "credential");
        assertThat(result.status()).isEqualTo(ConnectionStatus.UNAVAILABLE);
        assertThat(result.errorCode()).isEqualTo("SSH_HOST_KEY_VERIFICATION_FAILED");
    }
}

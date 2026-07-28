package devops.projectmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import devops.projectmanagement.api.ProjectManagementException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialCryptoServiceTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithRandomNonceAndDecryptsOnlyWithConfiguredKey() {
        CredentialCryptoService crypto = new CredentialCryptoService("test-key", KEY);

        CredentialCryptoService.EncryptedPayload first = crypto.encrypt("secret-value");
        CredentialCryptoService.EncryptedPayload second = crypto.encrypt("secret-value");

        assertThat(first.payload()).isNotEqualTo(second.payload());
        assertThat(first.keyId()).isEqualTo("test-key");
        assertThat(first.algorithm()).isEqualTo("AES-256-GCM");
        assertThat(crypto.decrypt(first.payload())).isEqualTo("secret-value");
    }

    @Test
    void rejectsEncryptionWhenKeyIsNotConfigured() {
        CredentialCryptoService crypto = new CredentialCryptoService("test-key", "");

        assertThatThrownBy(() -> crypto.encrypt("secret-value"))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("CREDENTIAL_ENCRYPTION_NOT_CONFIGURED");
    }
}

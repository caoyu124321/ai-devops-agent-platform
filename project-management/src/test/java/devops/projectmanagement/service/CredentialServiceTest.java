package devops.projectmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import devops.iam.contract.AuthorizationService;
import devops.projectmanagement.api.ProjectManagementException;
import devops.projectmanagement.dao.CredentialDao;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.security.CredentialCryptoService;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CredentialServiceTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void createsOnlyEncryptedCredentialVersionAndReturnsMetadata() {
        CredentialDao dao = mock(CredentialDao.class);
        when(dao.findById(any())).thenAnswer(invocation -> Optional.of(row(invocation.getArgument(0), "tenant", 1, "ACTIVE")));
        CredentialService service = service(dao, mock(ProjectDao.class));

        CredentialService.CredentialView result = service.create("actor", "tenant", "生产 SSH", "SSH_PASSWORD",
                Map.of("username", "root", "password", "secret-password"));

        assertThat(result.type()).isEqualTo("SSH_PASSWORD");
        verify(dao).create(any(), eq("tenant"), eq("生产 SSH"), eq("SSH_PASSWORD"), eq("actor"), any());
        verify(dao).createVersion(any(), any(), eq(1), any(byte[].class), eq("test-key"), eq("AES-256-GCM"), eq("actor"), any());
        assertThat(result.toString()).doesNotContain("secret-password");
    }

    @Test
    void rejectsSecretWithFieldsThatDoNotMatchCredentialType() {
        CredentialDao dao = mock(CredentialDao.class);
        CredentialService service = service(dao, mock(ProjectDao.class));

        assertThatThrownBy(() -> service.create("actor", "tenant", "kube", "KUBECONFIG", Map.of("token", "value")))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("CREDENTIAL_PAYLOAD_INVALID");
    }

    @Test
    void rotationAdvancesVersionAndNeverExposesPlaintext() {
        CredentialDao dao = mock(CredentialDao.class);
        CredentialDao.CredentialRow old = row("credential", "tenant", 1, "ACTIVE");
        CredentialDao.CredentialRow rotated = row("credential", "tenant", 2, "ACTIVE");
        when(dao.findById("credential")).thenReturn(Optional.of(old), Optional.of(rotated));
        when(dao.rotate(eq("credential"), eq(1), any())).thenReturn(true);
        CredentialService service = service(dao, mock(ProjectDao.class));

        CredentialService.CredentialView result = service.rotate("actor", "credential", 1,
                Map.of("username", "root", "password", "changed"));

        assertThat(result.version()).isEqualTo(2);
        verify(dao).createVersion(any(), eq("credential"), eq(2), any(byte[].class), any(), any(), eq("actor"), any());
        assertThat(result.toString()).doesNotContain("changed");
    }

    @Test
    void refusesCrossTenantCredentialGrant() {
        CredentialDao dao = mock(CredentialDao.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        when(dao.findById("credential")).thenReturn(Optional.of(row("credential", "tenant-a", 1, "ACTIVE")));
        when(projectDao.findById("project")).thenReturn(Optional.of(project("project", "tenant-b")));
        CredentialService service = service(dao, projectDao);

        assertThatThrownBy(() -> service.grant("actor", "credential", "project"))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("TENANT_MISMATCH");
    }

    @Test
    void rejectsDisabledCredentialBeforeRotation() {
        CredentialDao dao = mock(CredentialDao.class);
        when(dao.findById("credential")).thenReturn(Optional.of(row("credential", "tenant", 1, "DISABLED")));
        CredentialService service = service(dao, mock(ProjectDao.class));

        assertThatThrownBy(() -> service.rotate("actor", "credential", 1, Map.of("username", "root", "password", "x")))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("CREDENTIAL_DISABLED");
    }

    private CredentialService service(CredentialDao dao, ProjectDao projectDao) {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        doNothing().when(authorizationService).requireAuthorization(any());
        return new CredentialService(dao, projectDao, authorizationService,
                new CredentialCryptoService("test-key", KEY));
    }

    private CredentialDao.CredentialRow row(String id, String tenantId, int version, String status) {
        Instant now = Instant.now();
        return new CredentialDao.CredentialRow(id, tenantId, "credential", "SSH_PASSWORD", status, version, "creator", now, now);
    }

    private ProjectDao.ProjectRow project(String id, String tenantId) {
        Instant now = Instant.now();
        return new ProjectDao.ProjectRow(id, tenantId, "project", null, 1, "creator", now, now);
    }
}

package devops.projectmanagement.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import devops.iam.contract.AuthorizationService;
import devops.projectmanagement.api.ProjectManagementException;
import devops.projectmanagement.dao.CredentialDao;
import devops.projectmanagement.dao.EnvironmentDao;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.domain.ConnectionStatus;
import devops.projectmanagement.environment.EnvironmentConnectionValidator;
import devops.projectmanagement.environment.EnvironmentTarget;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnvironmentServiceTest {
    @Test
    void createsKubernetesEnvironmentWithGrantedCredentialAndVersion() {
        EnvironmentDao dao = mock(EnvironmentDao.class); ProjectDao projects = mock(ProjectDao.class); CredentialDao credentials = mock(CredentialDao.class);
        when(projects.findById("project")).thenReturn(Optional.of(project())); when(credentials.findById("credential")).thenReturn(Optional.of(credential("KUBECONFIG", "ACTIVE"))); when(credentials.isGranted("credential", "project")).thenReturn(true);
        when(dao.find(any())).thenAnswer(invocation -> Optional.of(environment(invocation.getArgument(0), 1, "HEALTHY")));
        EnvironmentService service = service(dao, projects, credentials);
        service.create("actor", "project", "dev", "DEV", "credential", kube());
        verify(dao).create(any(), eq("tenant"), eq("project"), eq("dev"), eq("KUBERNETES"), eq("DEV"), eq(false), eq("HEALTHY"), any(), eq(null), eq("actor"), any());
        verify(dao).version(any(), any(), eq(1), eq("KUBERNETES"), eq("DEV"), eq("credential"), eq("actor"), any(), any());
    }
    @Test
    void rejectsCredentialNotGrantedToProject() {
        EnvironmentDao dao=mock(EnvironmentDao.class); ProjectDao projects=mock(ProjectDao.class); CredentialDao credentials=mock(CredentialDao.class); when(projects.findById("project")).thenReturn(Optional.of(project())); when(credentials.findById("credential")).thenReturn(Optional.of(credential("KUBECONFIG","ACTIVE"))); when(credentials.isGranted("credential","project")).thenReturn(false);
        assertThatThrownBy(()->service(dao,projects,credentials).create("actor","project","dev","DEV","credential",kube())).isInstanceOf(ProjectManagementException.class).extracting(e->((ProjectManagementException)e).code()).isEqualTo("CREDENTIAL_NOT_GRANTED");
    }
    @Test
    void rejectsKubernetesDefaultNamespaceOutsideAllowList() {
        EnvironmentDao dao=mock(EnvironmentDao.class); ProjectDao projects=mock(ProjectDao.class); CredentialDao credentials=mock(CredentialDao.class); when(projects.findById("project")).thenReturn(Optional.of(project()));
        EnvironmentTarget target=new EnvironmentTarget.KubernetesTarget("https://cluster",null,"default",List.of("application"));
        assertThatThrownBy(()->service(dao,projects,credentials).create("actor","project","dev","DEV","credential",target)).isInstanceOf(ProjectManagementException.class).extracting(e->((ProjectManagementException)e).code()).isEqualTo("TARGET_CONFIGURATION_INVALID");
    }
    private EnvironmentService service(EnvironmentDao d,ProjectDao p,CredentialDao c){AuthorizationService a=mock(AuthorizationService.class); doNothing().when(a).requireAuthorization(any()); EnvironmentConnectionValidator v=mock(EnvironmentConnectionValidator.class); when(v.validate(any(),any())).thenReturn(EnvironmentConnectionValidator.ValidationResult.healthy()); return new EnvironmentService(d,p,c,a,v);}
    private ProjectDao.ProjectRow project(){Instant n=Instant.now();return new ProjectDao.ProjectRow("project","tenant","project",null,1,"creator",n,n);} private CredentialDao.CredentialRow credential(String t,String s){Instant n=Instant.now();return new CredentialDao.CredentialRow("credential","tenant","credential",t,s,1,"creator",n,n);} private EnvironmentDao.EnvironmentRow environment(String id,int v,String status){Instant n=Instant.now();return new EnvironmentDao.EnvironmentRow(id,"tenant","project","dev","KUBERNETES","DEV",false,status,n,null,v,"creator",n,n);} private EnvironmentTarget kube(){return new EnvironmentTarget.KubernetesTarget("https://cluster",null,"application",List.of("application"));}
}

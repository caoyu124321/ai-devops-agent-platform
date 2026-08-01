package devops.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import devops.iam.contract.AuthorizationService;
import devops.iam.contract.AuthorizationDecision;
import devops.iam.contract.AuthorizationRequest;
import devops.pipeline.dao.PipelineDao;
import devops.pipeline.dao.PipelineRunDao;
import devops.pipeline.domain.RunStatus;
import devops.pipeline.plugin.PipelinePlugin;
import devops.pipeline.plugin.PluginCatalog;
import devops.pipeline.plugin.PluginDescriptor;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.dao.RepositoryDao;
import devops.projectmanagement.dao.EnvironmentDao;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PipelineRunServiceTest {
    private final PipelineRunDao runDao = mock(PipelineRunDao.class);
    private final PipelineDao pipelineDao = mock(PipelineDao.class);
    private final ProjectDao projectDao = mock(ProjectDao.class);
    private final RepositoryDao repositoryDao = mock(RepositoryDao.class);
    private final EnvironmentDao environmentDao = mock(EnvironmentDao.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private PipelineRunService service;
    private PipelinePlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(PipelinePlugin.class);
        when(plugin.descriptor()).thenReturn(new PluginDescriptor("example.task", "1.0.0", java.util.Set.of(), java.util.Set.of(), 60));
        when(plugin.execute(any())).thenReturn(PipelinePlugin.PluginExecutionResult.succeeded(Map.of("result", "ok"),
                List.of(new PipelinePlugin.LogEntry("INFO", "完成"))));
        PluginCatalog catalog = (name, version) -> "example.task".equals(name) ? Optional.of(plugin) : Optional.empty();
        service = new PipelineRunService(runDao, pipelineDao, projectDao, repositoryDao, environmentDao,
                authorizationService, catalog, new PipelineYamlParser());
        when(pipelineDao.findVersion("version-1")).thenReturn(Optional.of(version()));
        when(pipelineDao.findPipeline("pipeline-1")).thenReturn(Optional.of(pipeline()));
        when(projectDao.findById("project-1")).thenReturn(Optional.of(project()));
        when(repositoryDao.findById("repository-1")).thenReturn(Optional.of(repository()));
        when(authorizationService.authorize(any())).thenReturn(new AuthorizationDecision(AuthorizationDecision.Decision.ALLOW,
                "TEST", List.of(), "v1"));
    }

    @Test
    void createsQueuedRunAndStepSnapshot() {
        when(pipelineDao.listSteps("version-1")).thenReturn(List.of(step()));

        PipelineRunService.RunView view = service.create("user-1", "version-1", "main", null, "key-1");

        assertThat(view.status()).isEqualTo("QUEUED");
        verify(runDao).create(any());
        verify(runDao).createStep(any());
    }

    @Test
    void returnsExistingRunForSameIdempotencyKey() {
        PipelineRunDao.RunRow existing = run("run-1", "QUEUED");
        when(runDao.byIdempotency("project-1", "key-1")).thenReturn(Optional.of(existing));

        PipelineRunService.RunView view = service.create("user-1", "version-1", "main", null, "key-1");

        assertThat(view.id()).isEqualTo("run-1");
        verify(runDao, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void dispatchesThroughGenericPluginAndCompletesRun() {
        PipelineRunDao.RunRow queued = run("run-1", "QUEUED");
        PipelineRunDao.RunRow running = run("run-1", "RUNNING");
        when(runDao.find("run-1")).thenReturn(Optional.of(queued), Optional.of(running), Optional.of(running));
        when(runDao.updateRun(anyString(), anyString(), anyString(), any(), any(), any(), any())).thenReturn(true);
        when(runDao.updateStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), any())).thenReturn(true);
        when(runDao.steps("run-1")).thenReturn(List.of(stepRun()));

        service.dispatch("run-1");

        verify(plugin).execute(any());
        verify(runDao).createLog(any());
        verify(runDao).updateStep(eq("step-run-1"), eq("RUNNING"), eq("SUCCEEDED"), notNull(), notNull(), any(),
                isNull(), isNull());
        verify(runDao).updateRun(eq("run-1"), eq("RUNNING"), eq("SUCCEEDED"), notNull(), notNull(), isNull(), isNull());
    }

    @Test
    void resolvesDeclaredRunParametersBeforePersistingStepSnapshot() {
        when(pipelineDao.findVersion("version-1")).thenReturn(Optional.of(parameterizedVersion()));
        when(pipelineDao.listSteps("version-1")).thenReturn(List.of(parameterizedStep()));
        ArgumentCaptor<PipelineRunDao.StepRunRow> captured = ArgumentCaptor.forClass(PipelineRunDao.StepRunRow.class);

        service.create("user-1", "version-1", "main", null, Map.of("imageTag", "1.2.3"), "key-1");

        verify(runDao).createStep(captured.capture());
        assertThat(captured.getValue().inputJson()).contains("1.2.3").doesNotContain("${{");
    }

    @Test
    void validatesEnvironmentParameterOwnershipHealthAndDeploymentAuthorization() {
        when(pipelineDao.findVersion("version-1")).thenReturn(Optional.of(environmentVersion()));
        when(pipelineDao.listSteps("version-1")).thenReturn(List.of(environmentStep()));
        when(environmentDao.find("environment-1")).thenReturn(Optional.of(environment()));
        ArgumentCaptor<AuthorizationRequest> authorization = ArgumentCaptor.forClass(AuthorizationRequest.class);

        service.create("user-1", "version-1", "main", null, Map.of("target", "environment-1"), "key-1");

        verify(authorizationService, atLeast(3)).requireAuthorization(authorization.capture());
        assertThat(authorization.getAllValues()).anySatisfy(request -> {
            assertThat(request.resourceType()).isEqualTo("ENVIRONMENT");
            assertThat(request.actionCode()).isEqualTo("environment.deploy");
            assertThat(request.resourceId()).isEqualTo("environment-1");
        });
    }

    @Test
    void failsQueuedRunBeforePluginExecutionWhenPermissionWasRevoked() {
        PipelineRunDao.RunRow queued = run("run-1", "QUEUED");
        when(runDao.find("run-1")).thenReturn(Optional.of(queued));
        when(runDao.updateRun(anyString(), anyString(), anyString(), any(), any(), any(), any())).thenReturn(true);
        when(authorizationService.authorize(any())).thenReturn(AuthorizationDecision.deny("NO_PERMISSION"));

        service.dispatch("run-1");

        verify(plugin, never()).execute(any());
        verify(runDao).skipPending(eq("run-1"), notNull(), eq("RUN_AUTHORIZATION_REVOKED"), anyString());
        verify(runDao).updateRun(eq("run-1"), eq("RUNNING"), eq("FAILED"), notNull(), notNull(),
                eq("RUN_AUTHORIZATION_REVOKED"), anyString());
    }

    @Test
    void cancelsQueuedRunAndSkipsPendingSteps() {
        PipelineRunDao.RunRow queued = run("run-1", "QUEUED");
        PipelineRunDao.RunRow canceled = new PipelineRunDao.RunRow("run-1", "tenant-1", "project-1", "pipeline-1",
                "version-1", "repository-1", 1, "main", null, "CANCELED", null, "{}", "RUN_CANCELED",
                "用户取消运行", "user-1", Instant.EPOCH, null, Instant.EPOCH);
        when(runDao.find("run-1")).thenReturn(Optional.of(queued), Optional.of(canceled));
        when(runDao.updateRun(anyString(), anyString(), anyString(), any(), any(), any(), any())).thenReturn(true);
        when(runDao.steps("run-1")).thenReturn(List.of());

        PipelineRunService.RunView view = service.cancel("user-1", "run-1");

        assertThat(view.status()).isEqualTo("CANCELED");
        verify(runDao).skipPending(eq("run-1"), notNull(), eq("RUN_CANCELED"), anyString());
        verify(plugin, never()).cancel(anyString());
    }

    @Test
    void retriesTerminalRunUsingOriginalSourceSnapshot() {
        PipelineRunDao.RunRow failed = new PipelineRunDao.RunRow("run-1", "tenant-1", "project-1", "pipeline-1",
                "version-1", "repository-1", 1, "main", null, "FAILED", null, "{}", "BUILD_FAILED",
                "失败", "user-1", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
        when(runDao.find("run-1")).thenReturn(Optional.of(failed));
        when(pipelineDao.listSteps("version-1")).thenReturn(List.of(step()));

        PipelineRunService.RunView view = service.retry("user-1", "run-1");

        assertThat(view.status()).isEqualTo("QUEUED");
        assertThat(view.sourceBranch()).isEqualTo("main");
        verify(runDao).create(any());
    }

    private PipelineDao.PipelineVersionRow version() { return new PipelineDao.PipelineVersionRow("version-1", "pipeline-1", 1, yaml(), "hash", "repository-1", 1, null, "user-1", Instant.EPOCH); }
    private PipelineDao.PipelineRow pipeline() { return new PipelineDao.PipelineRow("pipeline-1", "tenant-1", "project-1", "流水线", null, true, 1, "user-1", Instant.EPOCH, Instant.EPOCH); }
    private ProjectDao.ProjectRow project() { return new ProjectDao.ProjectRow("project-1", "tenant-1", "项目", null, 1, "user-1", Instant.EPOCH, Instant.EPOCH); }
    private RepositoryDao.RepositoryRow repository() { return new RepositoryDao.RepositoryRow("repository-1", "tenant-1", "project-1", "https://github.com/org/repo.git", "main", 1, "HEALTHY", Instant.EPOCH, null, "user-1", Instant.EPOCH, Instant.EPOCH); }
    private EnvironmentDao.EnvironmentRow environment() { return new EnvironmentDao.EnvironmentRow("environment-1", "tenant-1", "project-1", "test", "KUBERNETES", "TEST", true, "HEALTHY", Instant.EPOCH, null, 2, "user-1", Instant.EPOCH, Instant.EPOCH); }
    private PipelineDao.StepRow step() { return new PipelineDao.StepRow("step-1", "version-1", "stage", 1, "task", 1, "example.task", "1.0.0", "{}", Instant.EPOCH); }
    private PipelineDao.PipelineVersionRow parameterizedVersion() { return new PipelineDao.PipelineVersionRow("version-1", "pipeline-1", 1, parameterizedYaml(), "hash", "repository-1", 1, null, "user-1", Instant.EPOCH); }
    private PipelineDao.StepRow parameterizedStep() { return new PipelineDao.StepRow("step-1", "version-1", "stage", 1, "task", 1, "example.task", "1.0.0", "{\"tag\":\"${{ parameters.imageTag }}\"}", Instant.EPOCH); }
    private PipelineDao.PipelineVersionRow environmentVersion() { return new PipelineDao.PipelineVersionRow("version-1", "pipeline-1", 1, environmentYaml(), "hash", "repository-1", 1, null, "user-1", Instant.EPOCH); }
    private PipelineDao.StepRow environmentStep() { return new PipelineDao.StepRow("step-1", "version-1", "stage", 1, "task", 1, "example.task", "1.0.0", "{\"environment\":\"${{ parameters.target }}\"}", Instant.EPOCH); }
    private PipelineRunDao.StepRunRow stepRun() { return new PipelineRunDao.StepRunRow("step-run-1", "run-1", "step-1", 1, "task", "example.task", "1.0.0", "{}", "PENDING", null, null, null, null, null); }
    private PipelineRunDao.RunRow run(String id, String status) { return new PipelineRunDao.RunRow(id, "tenant-1", "project-1", "pipeline-1", "version-1", "repository-1", 1, "main", null, status, null, "{}", null, null, "user-1", Instant.EPOCH, RunStatus.QUEUED.name().equals(status) ? null : Instant.EPOCH, null); }
    private String yaml() { return """
            apiVersion: ai-devops/v1
            name: test
            repository: repository-1
            stages:
              - name: build
                steps:
                  - id: task
                    uses: example.task@1.0.0
            """; }
    private String parameterizedYaml() { return """
            apiVersion: ai-devops/v1
            name: test
            repository: repository-1
            parameters:
              imageTag:
                type: string
                required: true
            stages:
              - name: build
                steps:
                  - id: task
                    uses: example.task@1.0.0
                    with:
                      tag: ${{ parameters.imageTag }}
            """; }
    private String environmentYaml() { return """
            apiVersion: ai-devops/v1
            name: test
            repository: repository-1
            parameters:
              target:
                type: environment
                required: true
            stages:
              - name: deploy
                steps:
                  - id: task
                    uses: example.task@1.0.0
                    with:
                      environment: ${{ parameters.target }}
            """; }
}

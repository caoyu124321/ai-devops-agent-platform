package devops.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import devops.iam.contract.AuthorizationService;
import devops.iam.api.IamException;
import devops.pipeline.api.PipelineValidationException;
import devops.pipeline.dao.PipelineDao;
import devops.pipeline.plugin.PipelinePlugin;
import devops.pipeline.plugin.PluginCatalog;
import devops.pipeline.plugin.PluginDescriptor;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.dao.RepositoryDao;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PipelineServiceTest {
    private final PipelineDao pipelineDao = mock(PipelineDao.class);
    private final ProjectDao projectDao = mock(ProjectDao.class);
    private final RepositoryDao repositoryDao = mock(RepositoryDao.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private PipelineService service;

    @BeforeEach
    void setUp() {
        PipelinePlugin plugin = new PipelinePlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return new PluginDescriptor("example.build", "1.0.0", java.util.Set.of("goal"), java.util.Set.of(), 60);
            }

            @Override
            public PluginInputValidation validateInput(Map<String, Object> input) {
                return input.containsKey("goal") ? PluginInputValidation.accepted()
                        : PluginInputValidation.rejected("缺少 goal 参数");
            }
        };
        PluginCatalog catalog = (name, version) -> "example.build".equals(name) && "1.0.0".equals(version)
                ? Optional.of(plugin) : Optional.empty();
        service = new PipelineService(pipelineDao, projectDao, repositoryDao, authorizationService,
                new PipelineYamlParser(), catalog);
        when(projectDao.findById("project-1")).thenReturn(Optional.of(project()));
        when(repositoryDao.findById("repository-1")).thenReturn(Optional.of(repository()));
    }

    @Test
    void validatesPluginThroughCatalogWithoutKnowingTaskType() {
        PipelineService.ValidationView result = service.validate("user-1", "project-1", validYaml());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsUnavailablePluginBeforePersistingDefinition() {
        assertThatThrownBy(() -> service.create("user-1", "project-1", "构建", null, validYaml().replace("example.build@1.0.0", "unknown@1.0.0")))
                .isInstanceOf(PipelineValidationException.class)
                .extracting("code")
                .isEqualTo("PIPELINE_VALIDATION_FAILED");
    }

    @Test
    void persistsImmutableVersionAndParsedStepWhenDefinitionIsCreated() {
        when(pipelineDao.findPipeline(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.of(new PipelineDao.PipelineRow(id, "tenant-1", "project-1", "构建", null, true,
                    1, "user-1", Instant.EPOCH, Instant.EPOCH));
        });

        PipelineService.PipelineView view = service.create("user-1", "project-1", "构建", null, validYaml());

        assertThat(view.version()).isEqualTo(1);
        verify(pipelineDao).createPipeline(any());
        verify(pipelineDao).createVersion(any());
        verify(pipelineDao).createStep(any());
    }

    @Test
    void rejectsRepositoryFromAnotherProject() {
        when(repositoryDao.findById("repository-1")).thenReturn(Optional.of(new RepositoryDao.RepositoryRow("repository-1", "tenant-1",
                "other-project", "https://github.com/example/repo", "main", 1, "HEALTHY", null, null, "user-1", Instant.EPOCH, Instant.EPOCH)));

        PipelineService.ValidationView result = service.validate("user-1", "project-1", validYaml());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(error -> error.ruleCode()).contains("REPOSITORY_NOT_VISIBLE");
    }

    @Test
    void rejectsUpdateWhenOptimisticVersionDoesNotMatch() {
        PipelineDao.PipelineRow existing = new PipelineDao.PipelineRow("pipeline-1", "tenant-1", "project-1", "构建",
                null, true, 1, "user-1", Instant.EPOCH, Instant.EPOCH);
        when(pipelineDao.findPipeline("pipeline-1")).thenReturn(Optional.of(existing));
        when(pipelineDao.updatePipeline(eq("pipeline-1"), eq(1), eq("构建"), eq(null), any())).thenReturn(false);

        assertThatThrownBy(() -> service.update("user-1", "pipeline-1", 1, "构建", null, validYaml()))
                .isInstanceOfSatisfying(devops.pipeline.api.PipelineException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PIPELINE_VERSION_CONFLICT"));
    }

    @Test
    void forwardsAuthorizationDenialWithoutPersistingPipeline() {
        doThrow(new IamException("ACCESS_DENIED", org.springframework.http.HttpStatus.FORBIDDEN, "拒绝"))
                .when(authorizationService).requireAuthorization(any());

        assertThatThrownBy(() -> service.create("user-1", "project-1", "构建", null, validYaml()))
                .isInstanceOf(IamException.class);
        verify(pipelineDao, org.mockito.Mockito.never()).createPipeline(any());
    }

    private ProjectDao.ProjectRow project() {
        return new ProjectDao.ProjectRow("project-1", "tenant-1", "项目", null, 1, "user-1", Instant.EPOCH, Instant.EPOCH);
    }

    private RepositoryDao.RepositoryRow repository() {
        return new RepositoryDao.RepositoryRow("repository-1", "tenant-1", "project-1", "https://github.com/example/repo",
                "main", 1, "HEALTHY", null, null, "user-1", Instant.EPOCH, Instant.EPOCH);
    }

    private String validYaml() {
        return """
                apiVersion: ai-devops/v1
                name: build
                repository: repository-1
                stages:
                  - name: build
                    steps:
                      - id: compile
                        uses: example.build@1.0.0
                        with:
                          goal: package
                """;
    }
}

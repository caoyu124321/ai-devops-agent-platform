package devops.pipeline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationScope;
import devops.iam.contract.AuthorizationService;
import devops.pipeline.api.PipelineException;
import devops.pipeline.api.PipelineValidationException;
import devops.pipeline.dao.PipelineDao;
import devops.pipeline.domain.PipelineDefinition;
import devops.pipeline.domain.PipelineValidationError;
import devops.pipeline.plugin.PipelinePlugin;
import devops.pipeline.plugin.PluginCatalog;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.dao.RepositoryDao;
import devops.projectmanagement.domain.ConnectionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 流水线服务负责版本与资源授权；具体任务能力仅通过插件目录验证，核心不依赖任务实现。 */
@Service
public class PipelineService {
    private static final String PIPELINE_RESOURCE = "PIPELINE";
    private static final String ACTION_VIEW = "pipeline.view";
    private static final String ACTION_EDIT = "pipeline.edit";
    private static final String REPOSITORY_RESOURCE = "REPOSITORY";
    private static final String REPOSITORY_USE = "repository.use";
    private static final Pattern NAME_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9 _.,:;()（）\\-]+");
    private final PipelineDao pipelineDao;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final AuthorizationService authorizationService;
    private final PipelineYamlParser yamlParser;
    private final PluginCatalog pluginCatalog;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PipelineService(PipelineDao pipelineDao, ProjectDao projectDao, RepositoryDao repositoryDao,
                           AuthorizationService authorizationService, PipelineYamlParser yamlParser,
                           PluginCatalog pluginCatalog) {
        this.pipelineDao = pipelineDao;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.authorizationService = authorizationService;
        this.yamlParser = yamlParser;
        this.pluginCatalog = pluginCatalog;
    }

    public ValidationView validate(String actorId, String projectId, String yamlContent) {
        ProjectDao.ProjectRow project = requireProject(projectId);
        require(actorId, project, ACTION_EDIT);
        return validateDocument(actorId, project, yamlContent);
    }

    @Transactional
    public PipelineView create(String actorId, String projectId, String name, String description, String yamlContent) {
        ProjectDao.ProjectRow project = requireProject(projectId);
        require(actorId, project, ACTION_EDIT);
        validateName(name);
        PipelineDefinition definition = requireValid(actorId, project, yamlContent);
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        PipelineDao.PipelineRow row = new PipelineDao.PipelineRow(id, project.tenantId(), project.id(), name.trim(),
                normalizeDescription(description), true, 1, actorId, now, now);
        try {
            pipelineDao.createPipeline(row);
            createVersion(id, 1, actorId, yamlContent, definition, project, now);
        } catch (DuplicateKeyException exception) {
            throw error("PIPELINE_NAME_EXISTS", HttpStatus.CONFLICT, "项目内流水线名称已存在");
        }
        return view(requirePipeline(id));
    }

    public List<PipelineView> list(String actorId, String projectId) {
        ProjectDao.ProjectRow project = requireProject(projectId);
        require(actorId, project, ACTION_VIEW);
        return pipelineDao.listPipelines(projectId).stream().map(this::view).toList();
    }

    public PipelineView get(String actorId, String pipelineId) {
        PipelineDao.PipelineRow row = requirePipeline(pipelineId);
        require(actorId, requireProject(row.projectId()), ACTION_VIEW);
        return view(row);
    }

    @Transactional
    public PipelineView update(String actorId, String pipelineId, int expectedVersion, String name, String description,
                               String yamlContent) {
        PipelineDao.PipelineRow current = requirePipeline(pipelineId);
        ProjectDao.ProjectRow project = requireProject(current.projectId());
        require(actorId, project, ACTION_EDIT);
        validateName(name);
        if (expectedVersion < 1) {
            throw error("PIPELINE_VERSION_CONFLICT", HttpStatus.CONFLICT, "流水线版本不匹配");
        }
        PipelineDefinition definition = requireValid(actorId, project, yamlContent);
        Instant now = Instant.now();
        try {
            if (!pipelineDao.updatePipeline(pipelineId, expectedVersion, name.trim(), normalizeDescription(description), now)) {
                throw error("PIPELINE_VERSION_CONFLICT", HttpStatus.CONFLICT, "流水线版本不匹配");
            }
            createVersion(pipelineId, expectedVersion + 1, actorId, yamlContent, definition, project, now);
        } catch (DuplicateKeyException exception) {
            throw error("PIPELINE_NAME_EXISTS", HttpStatus.CONFLICT, "项目内流水线名称已存在");
        }
        return view(requirePipeline(pipelineId));
    }

    @Transactional
    public PipelineView setEnabled(String actorId, String pipelineId, int expectedVersion, boolean enabled) {
        PipelineDao.PipelineRow current = requirePipeline(pipelineId);
        require(actorId, requireProject(current.projectId()), ACTION_EDIT);
        if (!pipelineDao.setEnabled(pipelineId, expectedVersion, enabled, Instant.now())) {
            throw error("PIPELINE_VERSION_CONFLICT", HttpStatus.CONFLICT, "流水线版本不匹配");
        }
        return view(requirePipeline(pipelineId));
    }

    public List<PipelineVersionView> listVersions(String actorId, String pipelineId) {
        PipelineDao.PipelineRow pipeline = requirePipeline(pipelineId);
        require(actorId, requireProject(pipeline.projectId()), ACTION_VIEW);
        return pipelineDao.listVersions(pipelineId).stream().map(this::versionView).toList();
    }

    private ValidationView validateDocument(String actorId, ProjectDao.ProjectRow project, String yamlContent) {
        PipelineYamlParser.ParseResult parsed = yamlParser.parse(yamlContent);
        if (!parsed.valid()) {
            return new ValidationView(false, parsed.errors());
        }
        List<PipelineValidationError> errors = semanticErrors(actorId, project, parsed.definition());
        return new ValidationView(errors.isEmpty(), errors);
    }

    private PipelineDefinition requireValid(String actorId, ProjectDao.ProjectRow project, String yamlContent) {
        PipelineYamlParser.ParseResult parsed = yamlParser.parse(yamlContent);
        List<PipelineValidationError> errors = new ArrayList<>(parsed.errors());
        if (parsed.valid()) {
            errors.addAll(semanticErrors(actorId, project, parsed.definition()));
        }
        if (!errors.isEmpty()) {
            throw new PipelineValidationException(errors);
        }
        return parsed.definition();
    }

    private List<PipelineValidationError> semanticErrors(String actorId, ProjectDao.ProjectRow project,
                                                         PipelineDefinition definition) {
        List<PipelineValidationError> errors = new ArrayList<>();
        RepositoryDao.RepositoryRow repository = repositoryDao.findById(definition.repositoryId()).orElse(null);
        if (repository == null || !project.id().equals(repository.projectId()) || !project.tenantId().equals(repository.tenantId())) {
            errors.add(validationError("$.repository", "REPOSITORY_NOT_VISIBLE", "仓库不存在或不属于当前项目", "请选择当前项目已配置仓库"));
        } else if (!ConnectionStatus.HEALTHY.name().equals(repository.connectionStatus())) {
            errors.add(validationError("$.repository", "REPOSITORY_UNAVAILABLE", "仓库当前不可用", "请重新校验仓库后再保存流水线"));
        } else {
            requireRepositoryUse(actorId, project, repository);
        }
        int stageIndex = 0;
        for (PipelineDefinition.PipelineStage stage : definition.stages()) {
            int stepIndex = 0;
            for (PipelineDefinition.PipelineStep step : stage.steps()) {
                String path = "$.stages[" + stageIndex + "].steps[" + stepIndex + "]";
                PipelinePlugin plugin = pluginCatalog.find(step.pluginName(), step.pluginVersion()).orElse(null);
                if (plugin == null) {
                    errors.add(validationError(path + ".uses", "PLUGIN_NOT_AVAILABLE", "插件不存在、版本不匹配或已停用", "请选择已注册的精确插件版本"));
                } else {
                    PipelinePlugin.PluginInputValidation validation = plugin.validateInput(step.input());
                    if (!validation.valid()) {
                        errors.add(validationError(path + ".with", "PLUGIN_INPUT_INVALID",
                                validation.message() == null ? "插件输入不合法" : validation.message(), "请按插件描述符调整输入"));
                    }
                }
                stepIndex++;
            }
            stageIndex++;
        }
        return errors;
    }

    private void createVersion(String pipelineId, int versionNo, String actorId, String yamlContent,
                               PipelineDefinition definition, ProjectDao.ProjectRow project, Instant now) {
        RepositoryDao.RepositoryRow repository = repositoryDao.findById(definition.repositoryId())
                .orElseThrow(() -> error("REPOSITORY_NOT_FOUND", HttpStatus.NOT_FOUND, "仓库不存在或不可见"));
        String versionId = UUID.randomUUID().toString();
        pipelineDao.createVersion(new PipelineDao.PipelineVersionRow(versionId, pipelineId, versionNo, yamlContent,
                sha256(yamlContent), repository.id(), repository.currentVersionNo(), definition.defaultBranch(), actorId, now));
        for (PipelineDefinition.PipelineStage stage : definition.stages()) {
            for (PipelineDefinition.PipelineStep step : stage.steps()) {
                pipelineDao.createStep(new PipelineDao.StepRow(UUID.randomUUID().toString(), versionId, stage.name(),
                        stage.sequenceNo(), step.id(), step.sequenceNo(), step.pluginName(), step.pluginVersion(),
                        toJson(step.input()), now));
            }
        }
    }

    private void require(String actorId, ProjectDao.ProjectRow project, String action) {
        authorizationService.requireAuthorization(new AuthorizationRequest(
                new AuthenticatedSubject(actorId, null, Instant.now()), PIPELINE_RESOURCE, project.id(), action,
                new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT, project.tenantId(), project.id(), null, null), Map.of()));
    }

    /**
     * 编辑流水线并不等于可以使用任意仓库。仓库使用权限在保存和校验时单独判定，
     * 避免拥有流水线编辑权的成员把无权访问的仓库写入定义。
     */
    private void requireRepositoryUse(String actorId, ProjectDao.ProjectRow project, RepositoryDao.RepositoryRow repository) {
        authorizationService.requireAuthorization(new AuthorizationRequest(
                new AuthenticatedSubject(actorId, null, Instant.now()), REPOSITORY_RESOURCE, repository.id(), REPOSITORY_USE,
                new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT, project.tenantId(), project.id(), null, null), Map.of()));
    }

    private PipelineDao.PipelineRow requirePipeline(String pipelineId) {
        return pipelineDao.findPipeline(pipelineId)
                .orElseThrow(() -> error("PIPELINE_NOT_FOUND", HttpStatus.NOT_FOUND, "流水线不存在或不可见"));
    }

    private ProjectDao.ProjectRow requireProject(String projectId) {
        return projectDao.findById(projectId)
                .orElseThrow(() -> error("PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "项目不存在或不可见"));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 128 || !NAME_PATTERN.matcher(name.trim()).matches()) {
            throw error("PIPELINE_NAME_INVALID", HttpStatus.BAD_REQUEST, "流水线名称不符合要求");
        }
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        if (description.trim().length() > 500) {
            throw error("PIPELINE_DESCRIPTION_INVALID", HttpStatus.BAD_REQUEST, "流水线说明不能超过 500 个字符");
        }
        return description.trim();
    }

    private String toJson(Map<String, Object> input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw error("PIPELINE_INPUT_SERIALIZATION_FAILED", HttpStatus.BAD_REQUEST, "插件输入无法序列化");
        }
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw error("PIPELINE_HASH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "无法生成流水线内容摘要");
        }
    }

    private PipelineValidationError validationError(String path, String code, String message, String suggestion) {
        return new PipelineValidationError(path, -1, -1, code, message, suggestion);
    }

    private PipelineException error(String code, HttpStatus status, String message) {
        return new PipelineException(code, status, message);
    }

    private PipelineView view(PipelineDao.PipelineRow row) {
        return new PipelineView(row.id(), row.tenantId(), row.projectId(), row.name(), row.description(), row.enabled(),
                row.currentVersionNo(), row.createdAt(), row.updatedAt());
    }

    private PipelineVersionView versionView(PipelineDao.PipelineVersionRow row) {
        return new PipelineVersionView(row.id(), row.pipelineId(), row.versionNo(), row.contentSha256(), row.repositoryId(),
                row.repositoryVersionNo(), row.sourceDefaultBranch(), row.createdAt());
    }

    public record ValidationView(boolean valid, List<PipelineValidationError> errors) {
    }

    public record PipelineView(String id, String tenantId, String projectId, String name, String description, boolean enabled,
                               int version, Instant createdAt, Instant updatedAt) {
    }

    public record PipelineVersionView(String id, String pipelineId, int version, String contentSha256, String repositoryId,
                                      int repositoryVersion, String sourceDefaultBranch, Instant createdAt) {
    }
}

package devops.pipeline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationScope;
import devops.iam.contract.AuthorizationService;
import devops.pipeline.api.PipelineException;
import devops.pipeline.dao.PipelineDao;
import devops.pipeline.dao.PipelineRunDao;
import devops.pipeline.domain.PipelineDefinition;
import devops.pipeline.domain.RunStatus;
import devops.pipeline.domain.StepRunStatus;
import devops.pipeline.plugin.PipelinePlugin;
import devops.pipeline.plugin.PluginCatalog;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.dao.RepositoryDao;
import devops.projectmanagement.dao.EnvironmentDao;
import devops.projectmanagement.domain.ConnectionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通用调度器只转换运行状态并调用插件契约，绝不按具体构建或部署任务类型分支。 */
@Service
public class PipelineRunService {
    private static final String PIPELINE_RESOURCE = "PIPELINE";
    private static final String ACTION_RUN = "pipeline.run";
    private static final String ACTION_CANCEL = "pipeline.cancel";
    private final PipelineRunDao runDao;
    private final PipelineDao pipelineDao;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final EnvironmentDao environmentDao;
    private final AuthorizationService authorizationService;
    private final PluginCatalog pluginCatalog;
    private final PipelineYamlParser yamlParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PipelineRunService(PipelineRunDao runDao, PipelineDao pipelineDao, ProjectDao projectDao,
                              RepositoryDao repositoryDao, EnvironmentDao environmentDao,
                              AuthorizationService authorizationService, PluginCatalog pluginCatalog,
                              PipelineYamlParser yamlParser) {
        this.runDao = runDao;
        this.pipelineDao = pipelineDao;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.environmentDao = environmentDao;
        this.authorizationService = authorizationService;
        this.pluginCatalog = pluginCatalog;
        this.yamlParser = yamlParser;
    }

    @Transactional
    public RunView create(String actorId, String pipelineVersionId, String branch, String commit,
                          Map<String, Object> parameters, String idempotencyKey) {
        PipelineDao.PipelineVersionRow version = requireVersion(pipelineVersionId);
        PipelineDao.PipelineRow pipeline = requirePipeline(version.pipelineId());
        ProjectDao.ProjectRow project = requireProject(pipeline.projectId());
        require(actorId, project, ACTION_RUN);
        RepositoryDao.RepositoryRow repository = requireRepository(project, version.repositoryId());
        requireRepositoryUse(actorId, project, repository);
        if (!pipeline.enabled()) {
            throw error("PIPELINE_DISABLED", HttpStatus.CONFLICT, "流水线已停用，不能创建运行");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            validateIdempotencyKey(idempotencyKey);
            var existing = runDao.byIdempotency(project.id(), idempotencyKey.trim());
            if (existing.isPresent()) {
                return view(existing.get());
            }
        }
        Source source = source(branch, commit, version.sourceDefaultBranch());
        PipelineDefinition definition = requireDefinition(version);
        RuntimeParameters runtimeParameters = runtimeParameters(actorId, project, definition, parameters);
        String runId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        PipelineRunDao.RunRow row = new PipelineRunDao.RunRow(runId, project.tenantId(), project.id(), pipeline.id(), version.id(),
                version.repositoryId(), version.repositoryVersionNo(), source.branch(), source.commit(), RunStatus.QUEUED.name(),
                blankToNull(idempotencyKey), snapshot(version, source, runtimeParameters), null, null, actorId, now, null, null);
        runDao.create(row);
        for (PipelineDao.StepRow step : pipelineDao.listSteps(version.id())) {
            runDao.createStep(new PipelineRunDao.StepRunRow(UUID.randomUUID().toString(), runId, step.id(), step.stepSequenceNo(),
                    step.stepId(), step.pluginName(), step.pluginVersion(), json(resolveInput(input(step.inputJson()), runtimeParameters.values())), StepRunStatus.PENDING.name(), null,
                    null, null, null, null));
        }
        return view(row);
    }

    @Transactional
    public RunView create(String actorId, String pipelineVersionId, String branch, String commit, String idempotencyKey) {
        return create(actorId, pipelineVersionId, branch, commit, Map.of(), idempotencyKey);
    }

    /** 由受控 Worker 调用；条件更新确保同一个队列运行只会被一个调度器获取。 */
    @Transactional
    public void dispatch(String runId) {
        PipelineRunDao.RunRow queued = requireRun(runId);
        Instant runStartedAt = Instant.now();
        if (!RunStatus.QUEUED.name().equals(queued.status())
                || !runDao.updateRun(runId, RunStatus.QUEUED.name(), RunStatus.RUNNING.name(), runStartedAt, null, null, null)) {
            return;
        }
        if (!eligibleForDispatch(queued)) {
            failBeforeExecution(runId, runStartedAt, "RUN_AUTHORIZATION_REVOKED", "运行所需权限或配置已失效");
            return;
        }
        for (PipelineRunDao.StepRunRow step : runDao.steps(runId)) {
            if (isCanceled(runId)) {
                runDao.skipPending(runId, Instant.now(), "RUN_CANCELED", "运行已取消");
                return;
            }
            Instant stepStartedAt = Instant.now();
            if (!runDao.updateStep(step.id(), StepRunStatus.PENDING.name(), StepRunStatus.RUNNING.name(), stepStartedAt, null, null, null, null)) {
                continue;
            }
            PipelinePlugin.PluginExecutionResult result = execute(queued, step);
            Instant now = Instant.now();
            PipelineRunDao.RunRow latest = requireRun(runId);
            if (RunStatus.CANCELED.name().equals(latest.status())) {
                runDao.updateStep(step.id(), StepRunStatus.RUNNING.name(), StepRunStatus.CANCELED.name(), stepStartedAt, now,
                        null, "RUN_CANCELED", "运行已取消");
                runDao.skipPending(runId, now, "RUN_CANCELED", "运行已取消");
                return;
            }
            persistLogs(step.id(), result.logs(), now);
            StepRunStatus status = map(result.status());
            runDao.updateStep(step.id(), StepRunStatus.RUNNING.name(), status.name(), stepStartedAt, now,
                    json(result.output()), result.failureCode(), result.failureMessage());
            if (status != StepRunStatus.SUCCEEDED) {
                runDao.skipPending(runId, now, "DEPENDENCY_NOT_SATISFIED", "前置步骤未成功完成");
                RunStatus runStatus = switch (status) {
                    case CANCELED -> RunStatus.CANCELED;
                    case TIMED_OUT -> RunStatus.TIMED_OUT;
                    default -> RunStatus.FAILED;
                };
                runDao.updateRun(runId, RunStatus.RUNNING.name(), runStatus.name(), runStartedAt, now,
                        result.failureCode(), result.failureMessage());
                return;
            }
        }
        runDao.updateRun(runId, RunStatus.RUNNING.name(), RunStatus.SUCCEEDED.name(), runStartedAt, Instant.now(), null, null);
    }

    @Transactional
    public RunView cancel(String actorId, String runId) {
        PipelineRunDao.RunRow run = requireRun(runId);
        require(actorId, requireProject(run.projectId()), ACTION_CANCEL);
        if (isTerminal(run.status())) {
            return view(run);
        }
        Instant now = Instant.now();
        if (runDao.updateRun(runId, run.status(), RunStatus.CANCELED.name(), run.startedAt(), now, "RUN_CANCELED", "用户取消运行")) {
            runDao.skipPending(runId, now, "RUN_CANCELED", "用户取消运行");
            runDao.steps(runId).stream().filter(step -> StepRunStatus.RUNNING.name().equals(step.status())).findFirst().ifPresent(step ->
                    pluginCatalog.find(step.pluginName(), step.pluginVersion()).ifPresent(plugin -> plugin.cancel(step.id())));
        }
        return view(requireRun(runId));
    }

    public RunDetailView get(String actorId, String runId) {
        PipelineRunDao.RunRow run = requireRun(runId);
        require(actorId, requireProject(run.projectId()), "pipeline.view");
        return new RunDetailView(view(run), runDao.steps(runId).stream().map(this::stepView).toList());
    }

    public List<LogView> logs(String actorId, String runId) {
        PipelineRunDao.RunRow run = requireRun(runId);
        require(actorId, requireProject(run.projectId()), "pipeline.view");
        return runDao.steps(runId).stream().flatMap(step -> runDao.logs(step.id()).stream()
                .map(log -> new LogView(step.stepId(), log.sequenceNo(), log.level(), log.message(), log.createdAt()))).toList();
    }

    @Transactional
    public RunView retry(String actorId, String runId) {
        PipelineRunDao.RunRow source = requireRun(runId);
        require(actorId, requireProject(source.projectId()), ACTION_RUN);
        if (!isTerminal(source.status())) {
            throw error("RUN_NOT_TERMINAL", HttpStatus.CONFLICT, "只能重试已结束的运行");
        }
        return create(actorId, source.pipelineVersionId(), source.sourceBranch(), source.sourceCommit(),
                parametersFromSnapshot(source.configurationSnapshot()), null);
    }

    private PipelinePlugin.PluginExecutionResult execute(PipelineRunDao.RunRow run, PipelineRunDao.StepRunRow step) {
        PipelinePlugin plugin = pluginCatalog.find(step.pluginName(), step.pluginVersion()).orElse(null);
        if (plugin == null) {
            return PipelinePlugin.PluginExecutionResult.failed("PLUGIN_UNAVAILABLE", "运行所需插件已不可用");
        }
        try {
            return plugin.execute(new PipelinePlugin.PluginExecutionRequest(UUID.randomUUID().toString(), run.id(), step.id(),
                    run.projectId(), run.repositoryId(), run.sourceBranch(), run.sourceCommit(), input(step.inputJson()),
                    runtimeContext(run.configurationSnapshot())));
        } catch (RuntimeException exception) {
            return PipelinePlugin.PluginExecutionResult.failed("PLUGIN_EXECUTION_FAILED", "插件执行失败");
        }
    }

    private void persistLogs(String stepRunId, List<PipelinePlugin.LogEntry> logs, Instant now) {
        int sequence = 1;
        for (PipelinePlugin.LogEntry log : logs) {
            String level = log.level() == null ? "INFO" : log.level();
            String message = log.message() == null ? "" : log.message();
            if (!List.of("INFO", "WARN", "ERROR").contains(level) || message.length() > 4000) {
                continue;
            }
            runDao.createLog(new PipelineRunDao.LogRow(UUID.randomUUID().toString(), stepRunId, sequence++, level, message, now));
        }
    }

    private Map<String, Object> input(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw error("STEP_INPUT_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, "步骤输入快照不完整");
        }
    }

    /**
     * 只向插件提供已脱敏且版本化的运行上下文；插件不得从上下文取得凭据原文，
     * 需要受控配置时应按环境和版本引用向预配置模块请求。
     */
    private Map<String, Object> runtimeContext(String snapshot) {
        try {
            return Map.copyOf(objectMapper.readValue(snapshot, new TypeReference<>() { }));
        } catch (JsonProcessingException exception) {
            throw error("RUN_SNAPSHOT_INVALID", HttpStatus.CONFLICT, "运行快照不可用");
        }
    }

    private String snapshot(PipelineDao.PipelineVersionRow version, Source source, RuntimeParameters runtimeParameters) {
        return json(Map.of("pipelineVersionId", version.id(), "repositoryId", version.repositoryId(),
                "repositoryVersion", version.repositoryVersionNo(), "branch", source.branch() == null ? "" : source.branch(),
                "commit", source.commit() == null ? "" : source.commit(), "parameters", runtimeParameters.values(),
                "environmentVersions", runtimeParameters.environmentVersions()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw error("RUN_SNAPSHOT_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "无法保存运行快照");
        }
    }

    private Source source(String branch, String commit, String configuredBranch) {
        String normalizedBranch = blankToNull(branch);
        String normalizedCommit = blankToNull(commit);
        if (normalizedBranch == null && normalizedCommit == null) {
            normalizedBranch = blankToNull(configuredBranch);
        }
        if (normalizedBranch == null && normalizedCommit == null) {
            throw error("SOURCE_REQUIRED", HttpStatus.BAD_REQUEST, "必须指定代码分支或提交");
        }
        if (normalizedBranch != null && normalizedCommit != null) {
            throw error("SOURCE_AMBIGUOUS", HttpStatus.BAD_REQUEST, "代码分支和提交不能同时指定");
        }
        return new Source(normalizedBranch, normalizedCommit);
    }

    /**
     * 运行参数在创建运行时完成类型、归属和权限校验，并将环境版本号放入脱敏快照。
     * 调度器和插件随后只消费这一通用上下文，不需要知道构建或部署任务的具体类型。
     */
    private RuntimeParameters runtimeParameters(String actorId, ProjectDao.ProjectRow project,
                                                PipelineDefinition definition, Map<String, Object> supplied) {
        Map<String, Object> input = supplied == null ? Map.of() : supplied;
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        Map<String, Integer> environmentVersions = new java.util.LinkedHashMap<>();
        for (String name : input.keySet()) {
            if (!definition.parameters().containsKey(name)) {
                throw error("PIPELINE_PARAMETER_UNDECLARED", HttpStatus.BAD_REQUEST, "传入了未声明的流水线参数");
            }
        }
        for (PipelineDefinition.PipelineParameter parameter : definition.parameters().values()) {
            Object value = input.get(parameter.name());
            if (value == null) {
                if (parameter.required()) {
                    throw error("PIPELINE_PARAMETER_REQUIRED", HttpStatus.BAD_REQUEST, "缺少必填流水线参数");
                }
                continue;
            }
            validateParameterType(parameter, value);
            values.put(parameter.name(), value);
            if (parameter.type() == PipelineDefinition.PipelineParameter.Type.ENVIRONMENT) {
                EnvironmentDao.EnvironmentRow environment = requireEnvironment(project, (String) value);
                requireEnvironmentDeploy(actorId, project, environment);
                environmentVersions.put(parameter.name(), environment.currentVersionNo());
            }
        }
        return new RuntimeParameters(Map.copyOf(values), Map.copyOf(environmentVersions));
    }

    private void validateParameterType(PipelineDefinition.PipelineParameter parameter, Object value) {
        boolean valid = switch (parameter.type()) {
            case STRING, ENVIRONMENT -> value instanceof String text && !text.isBlank();
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
        };
        if (!valid) {
            throw error("PIPELINE_PARAMETER_TYPE_INVALID", HttpStatus.BAD_REQUEST, "流水线参数类型不匹配");
        }
    }

    private Map<String, Object> resolveInput(Map<String, Object> source, Map<String, Object> parameters) {
        Map<String, Object> resolved = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> resolved.put(key, resolveValue(value, parameters)));
        return Map.copyOf(resolved);
    }

    private Object resolveValue(Object value, Map<String, Object> parameters) {
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> resolved = new java.util.LinkedHashMap<>();
            nested.forEach((key, nestedValue) -> resolved.put(String.valueOf(key), resolveValue(nestedValue, parameters)));
            return Map.copyOf(resolved);
        }
        if (value instanceof List<?> values) {
            return values.stream().map(item -> resolveValue(item, parameters)).toList();
        }
        if (!(value instanceof String text)) {
            return value;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\$\\{\\{\\s*parameters\\.([A-Za-z][A-Za-z0-9_-]{0,63})\\s*}}$")
                .matcher(text);
        if (!matcher.matches()) {
            return text;
        }
        Object resolved = parameters.get(matcher.group(1));
        if (resolved == null) {
            throw error("PIPELINE_PARAMETER_REQUIRED", HttpStatus.BAD_REQUEST, "步骤引用的流水线参数未提供");
        }
        return resolved;
    }

    private PipelineDefinition requireDefinition(PipelineDao.PipelineVersionRow version) {
        PipelineYamlParser.ParseResult parsed = yamlParser.parse(version.yamlContent());
        if (!parsed.valid()) {
            throw error("PIPELINE_VERSION_INVALID", HttpStatus.CONFLICT, "流水线版本已无法解析");
        }
        return parsed.definition();
    }

    private RepositoryDao.RepositoryRow requireRepository(ProjectDao.ProjectRow project, String repositoryId) {
        RepositoryDao.RepositoryRow repository = repositoryDao.findById(repositoryId)
                .orElseThrow(() -> error("REPOSITORY_NOT_FOUND", HttpStatus.NOT_FOUND, "仓库不存在或不可见"));
        if (!project.id().equals(repository.projectId()) || !project.tenantId().equals(repository.tenantId())) {
            throw error("REPOSITORY_NOT_FOUND", HttpStatus.NOT_FOUND, "仓库不存在或不可见");
        }
        if (!ConnectionStatus.HEALTHY.name().equals(repository.connectionStatus())) {
            throw error("REPOSITORY_UNAVAILABLE", HttpStatus.CONFLICT, "仓库当前不可用");
        }
        return repository;
    }

    private EnvironmentDao.EnvironmentRow requireEnvironment(ProjectDao.ProjectRow project, String environmentId) {
        EnvironmentDao.EnvironmentRow environment = environmentDao.find(environmentId)
                .orElseThrow(() -> error("ENVIRONMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "环境不存在或不可见"));
        if (!project.id().equals(environment.projectId()) || !project.tenantId().equals(environment.tenantId())) {
            throw error("ENVIRONMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "环境不存在或不可见");
        }
        if (!environment.enabled() || !ConnectionStatus.HEALTHY.name().equals(environment.connectionStatus())) {
            throw error("ENVIRONMENT_UNAVAILABLE", HttpStatus.CONFLICT, "环境当前不可用");
        }
        return environment;
    }

    private void requireRepositoryUse(String actor, ProjectDao.ProjectRow project, RepositoryDao.RepositoryRow repository) {
        authorizationService.requireAuthorization(new AuthorizationRequest(new AuthenticatedSubject(actor, null, Instant.now()),
                "REPOSITORY", repository.id(), "repository.use", new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT,
                project.tenantId(), project.id(), null, null), Map.of()));
    }

    private void requireEnvironmentDeploy(String actor, ProjectDao.ProjectRow project, EnvironmentDao.EnvironmentRow environment) {
        authorizationService.requireAuthorization(new AuthorizationRequest(new AuthenticatedSubject(actor, null, Instant.now()),
                "ENVIRONMENT", environment.id(), "environment.deploy", new AuthorizationScope(AuthorizationScope.ScopeType.ENVIRONMENT,
                project.tenantId(), project.id(), environment.id(), environmentLevel(environment.environmentLevel())), Map.of()));
    }

    private AuthorizationScope.EnvironmentLevel environmentLevel(String value) {
        try {
            return AuthorizationScope.EnvironmentLevel.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw error("ENVIRONMENT_LEVEL_INVALID", HttpStatus.CONFLICT, "环境等级配置无效");
        }
    }

    private boolean eligibleForDispatch(PipelineRunDao.RunRow run) {
        ProjectDao.ProjectRow project;
        try {
            project = requireProject(run.projectId());
            PipelineDao.PipelineRow pipeline = requirePipeline(run.pipelineId());
            if (!pipeline.enabled()) {
                return false;
            }
            RepositoryDao.RepositoryRow repository = requireRepository(project, run.repositoryId());
            if (!allowed(run.requestedBy(), "PIPELINE", project.id(), ACTION_RUN,
                    new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT, project.tenantId(), project.id(), null, null))) {
                return false;
            }
            if (!allowed(run.requestedBy(), "REPOSITORY", repository.id(), "repository.use",
                    new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT, project.tenantId(), project.id(), null, null))) {
                return false;
            }
            return environmentsEligibleForDispatch(run, project);
        } catch (PipelineException exception) {
            return false;
        }
    }

    private boolean environmentsEligibleForDispatch(PipelineRunDao.RunRow run, ProjectDao.ProjectRow project) {
        Map<String, Integer> versions = environmentVersionsFromSnapshot(run.configurationSnapshot());
        Map<String, Object> parameters = parametersFromSnapshot(run.configurationSnapshot());
        for (String parameterName : versions.keySet()) {
            Object value = parameters.get(parameterName);
            if (!(value instanceof String environmentId)) {
                return false;
            }
            EnvironmentDao.EnvironmentRow environment = requireEnvironment(project, environmentId);
            if (!allowed(run.requestedBy(), "ENVIRONMENT", environment.id(), "environment.deploy",
                    new AuthorizationScope(AuthorizationScope.ScopeType.ENVIRONMENT, project.tenantId(), project.id(), environment.id(),
                            environmentLevel(environment.environmentLevel())))) {
                return false;
            }
        }
        return true;
    }

    private boolean allowed(String actor, String resourceType, String resourceId, String action, AuthorizationScope scope) {
        return authorizationService.authorize(new AuthorizationRequest(new AuthenticatedSubject(actor, null, Instant.now()),
                resourceType, resourceId, action, scope, Map.of())).decision()
                == devops.iam.contract.AuthorizationDecision.Decision.ALLOW;
    }

    private void failBeforeExecution(String runId, Instant startedAt, String code, String message) {
        Instant now = Instant.now();
        runDao.skipPending(runId, now, code, message);
        runDao.updateRun(runId, RunStatus.RUNNING.name(), RunStatus.FAILED.name(), startedAt, now, code, message);
    }

    private Map<String, Object> parametersFromSnapshot(String snapshot) {
        return mapFromSnapshot(snapshot, "parameters");
    }

    private Map<String, Integer> environmentVersionsFromSnapshot(String snapshot) {
        Map<String, Object> values = mapFromSnapshot(snapshot, "environmentVersions");
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value instanceof Number number) {
                result.put(key, number.intValue());
            }
        });
        return Map.copyOf(result);
    }

    private Map<String, Object> mapFromSnapshot(String snapshot, String field) {
        try {
            Map<String, Object> root = objectMapper.readValue(snapshot, new TypeReference<>() { });
            Object value = root.get(field);
            if (!(value instanceof Map<?, ?> source)) {
                return Map.of();
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            source.forEach((key, item) -> {
                if (key instanceof String text && item != null) {
                    result.put(text, item);
                }
            });
            return Map.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw error("RUN_SNAPSHOT_INVALID", HttpStatus.CONFLICT, "运行快照不可用");
        }
    }

    private boolean isCanceled(String runId) { return RunStatus.CANCELED.name().equals(requireRun(runId).status()); }
    private boolean isTerminal(String status) { return !RunStatus.QUEUED.name().equals(status) && !RunStatus.RUNNING.name().equals(status); }
    private StepRunStatus map(PipelinePlugin.Status status) { return StepRunStatus.valueOf(status.name()); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void validateIdempotencyKey(String value) { if (value.trim().length() > 128) throw error("IDEMPOTENCY_KEY_INVALID", HttpStatus.BAD_REQUEST, "幂等键不能超过 128 个字符"); }
    private PipelineDao.PipelineVersionRow requireVersion(String id) { return pipelineDao.findVersion(id).orElseThrow(() -> error("PIPELINE_VERSION_NOT_FOUND", HttpStatus.NOT_FOUND, "流水线版本不存在或不可见")); }
    private PipelineDao.PipelineRow requirePipeline(String id) { return pipelineDao.findPipeline(id).orElseThrow(() -> error("PIPELINE_NOT_FOUND", HttpStatus.NOT_FOUND, "流水线不存在或不可见")); }
    private PipelineRunDao.RunRow requireRun(String id) { return runDao.find(id).orElseThrow(() -> error("RUN_NOT_FOUND", HttpStatus.NOT_FOUND, "运行不存在或不可见")); }
    private ProjectDao.ProjectRow requireProject(String id) { return projectDao.findById(id).orElseThrow(() -> error("PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "项目不存在或不可见")); }
    private void require(String actor, ProjectDao.ProjectRow project, String action) { authorizationService.requireAuthorization(new AuthorizationRequest(new AuthenticatedSubject(actor, null, Instant.now()), PIPELINE_RESOURCE, project.id(), action, new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT, project.tenantId(), project.id(), null, null), Map.of())); }
    private PipelineException error(String code, HttpStatus status, String message) { return new PipelineException(code, status, message); }
    private RunView view(PipelineRunDao.RunRow row) { return new RunView(row.id(), row.pipelineVersionId(), row.status(), row.sourceBranch(), row.sourceCommit(), row.createdAt(), row.startedAt(), row.finishedAt(), row.failureCode(), row.failureMessage()); }
    private StepView stepView(PipelineRunDao.StepRunRow row) { return new StepView(row.id(), row.stepId(), row.pluginName(), row.pluginVersion(), row.status(), row.failureCode(), row.failureMessage(), row.startedAt(), row.finishedAt()); }

    private record Source(String branch, String commit) { }
    private record RuntimeParameters(Map<String, Object> values, Map<String, Integer> environmentVersions) { }
    public record RunView(String id, String pipelineVersionId, String status, String sourceBranch, String sourceCommit, Instant createdAt, Instant startedAt, Instant finishedAt, String failureCode, String failureMessage) { }
    public record StepView(String id, String stepId, String pluginName, String pluginVersion, String status, String failureCode, String failureMessage, Instant startedAt, Instant finishedAt) { }
    public record RunDetailView(RunView run, List<StepView> steps) { }
    public record LogView(String stepId, int sequenceNo, String level, String message, Instant createdAt) { }
}

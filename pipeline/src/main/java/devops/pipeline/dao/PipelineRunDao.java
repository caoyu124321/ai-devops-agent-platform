package devops.pipeline.dao;

import devops.pipeline.persistence.mapper.PipelineRunMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 运行 DAO 封装状态条件更新，使调度器可用乐观条件避免重复消费。 */
@Repository
public class PipelineRunDao {
    private final PipelineRunMapper mapper;

    public PipelineRunDao(PipelineRunMapper mapper) { this.mapper = mapper; }

    public void create(RunRow row) { mapper.createRun(row.id(), row.tenantId(), row.projectId(), row.pipelineId(), row.pipelineVersionId(), row.repositoryId(), row.repositoryVersionNo(), row.sourceBranch(), row.sourceCommit(), row.status(), row.idempotencyKey(), row.configurationSnapshot(), row.requestedBy(), row.createdAt()); }
    public void createStep(StepRunRow row) { mapper.createStepRun(row.id(), row.runId(), row.pipelineStepId(), row.sequenceNo(), row.stepId(), row.pluginName(), row.pluginVersion(), row.inputJson(), row.status()); }
    public Optional<RunRow> find(String id) { return Optional.ofNullable(mapper.findRun(id)); }
    public Optional<RunRow> byIdempotency(String projectId, String key) { return Optional.ofNullable(mapper.findByIdempotencyKey(projectId, key)); }
    public List<RunRow> queued(int limit) { return mapper.listQueued(limit); }
    public List<StepRunRow> steps(String runId) { return mapper.listStepRuns(runId); }
    public boolean updateRun(String id, String expected, String status, Instant started, Instant finished, String code, String message) { return mapper.updateRunStatus(id, expected, status, started, finished, code, message) > 0; }
    public boolean updateStep(String id, String expected, String status, Instant started, Instant finished, String output, String code, String message) { return mapper.updateStepStatus(id, expected, status, started, finished, output, code, message) > 0; }
    public void skipPending(String runId, Instant now, String code, String message) { mapper.skipPending(runId, now, code, message); }
    public void createLog(LogRow row) { mapper.createLog(row.id(), row.stepRunId(), row.sequenceNo(), row.level(), row.message(), row.createdAt()); }
    public List<LogRow> logs(String stepRunId) { return mapper.listLogs(stepRunId).stream().map(row -> new LogRow(row.id(), row.stepRunId(), row.sequenceNo(), row.level(), row.message(), row.createdAt())).toList(); }

    public record RunRow(String id, String tenantId, String projectId, String pipelineId, String pipelineVersionId,
                         String repositoryId, int repositoryVersionNo, String sourceBranch, String sourceCommit, String status,
                         String idempotencyKey, String configurationSnapshot, String failureCode, String failureMessage,
                         String requestedBy, Instant createdAt, Instant startedAt, Instant finishedAt) { }
    public record StepRunRow(String id, String runId, String pipelineStepId, int sequenceNo, String stepId, String pluginName,
                             String pluginVersion, String inputJson, String status, String outputJson, String failureCode,
                             String failureMessage, Instant startedAt, Instant finishedAt) { }
    public record LogRow(String id, String stepRunId, int sequenceNo, String level, String message, Instant createdAt) { }
}

package devops.pipeline.dao;

import devops.pipeline.persistence.mapper.PipelineMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 流水线 DAO 只负责当前定义、版本与解析步骤快照的持久化。 */
@Repository
public class PipelineDao {
    private final PipelineMapper mapper;

    public PipelineDao(PipelineMapper mapper) {
        this.mapper = mapper;
    }

    public void createPipeline(PipelineRow row) {
        mapper.createPipeline(row.id(), row.tenantId(), row.projectId(), row.name(), row.description(), row.enabled(),
                row.currentVersionNo(), row.createdBy(), row.createdAt());
    }

    public void createVersion(PipelineVersionRow row) {
        mapper.createVersion(row.id(), row.pipelineId(), row.versionNo(), row.yamlContent(), row.contentSha256(), row.repositoryId(),
                row.repositoryVersionNo(), row.sourceDefaultBranch(), row.createdBy(), row.createdAt());
    }

    public void createStep(StepRow row) {
        mapper.createStep(row.id(), row.pipelineVersionId(), row.stageName(), row.stageSequenceNo(), row.stepId(), row.stepSequenceNo(),
                row.pluginName(), row.pluginVersion(), row.inputJson(), row.createdAt());
    }

    public Optional<PipelineRow> findPipeline(String pipelineId) { return Optional.ofNullable(mapper.findPipeline(pipelineId)); }

    public List<PipelineRow> listPipelines(String projectId) { return mapper.listPipelines(projectId); }

    public Optional<PipelineVersionRow> findVersion(String versionId) { return Optional.ofNullable(mapper.findVersion(versionId)); }

    public List<PipelineVersionRow> listVersions(String pipelineId) { return mapper.listVersions(pipelineId); }

    public List<StepRow> listSteps(String versionId) { return mapper.listSteps(versionId); }

    public boolean updatePipeline(String pipelineId, int expectedVersion, String name, String description, Instant now) {
        return mapper.updatePipeline(pipelineId, expectedVersion, name, description, now) > 0;
    }

    public boolean setEnabled(String pipelineId, int expectedVersion, boolean enabled, Instant now) {
        return mapper.setEnabled(pipelineId, expectedVersion, enabled, now) > 0;
    }

    public record PipelineRow(String id, String tenantId, String projectId, String name, String description, boolean enabled,
                              int currentVersionNo, String createdBy, Instant createdAt, Instant updatedAt) {
    }

    public record PipelineVersionRow(String id, String pipelineId, int versionNo, String yamlContent, String contentSha256,
                                     String repositoryId, int repositoryVersionNo, String sourceDefaultBranch, String createdBy,
                                     Instant createdAt) {
    }

    public record StepRow(String id, String pipelineVersionId, String stageName, int stageSequenceNo, String stepId,
                          int stepSequenceNo, String pluginName, String pluginVersion, String inputJson, Instant createdAt) {
    }
}

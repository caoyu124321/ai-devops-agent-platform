package devops.pipeline.persistence.mapper;

import devops.pipeline.dao.PipelineRunDao.RunRow;
import devops.pipeline.dao.PipelineRunDao.StepRunRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 运行映射仅提供状态与快照读写，不承担调度决策。 */
@Mapper
public interface PipelineRunMapper {
    @Insert("insert into pl_runs(id,tenant_id,project_id,pipeline_id,pipeline_version_id,repository_id,repository_version_no,source_branch,source_commit,status,idempotency_key,configuration_snapshot,requested_by,created_at) values(#{id},#{tenantId},#{projectId},#{pipelineId},#{pipelineVersionId},#{repositoryId},#{repositoryVersionNo},#{sourceBranch},#{sourceCommit},#{status},#{idempotencyKey},#{snapshot},#{requestedBy},#{createdAt})")
    int createRun(@Param("id") String id, @Param("tenantId") String tenantId, @Param("projectId") String projectId,
                  @Param("pipelineId") String pipelineId, @Param("pipelineVersionId") String pipelineVersionId,
                  @Param("repositoryId") String repositoryId, @Param("repositoryVersionNo") int repositoryVersionNo,
                  @Param("sourceBranch") String sourceBranch, @Param("sourceCommit") String sourceCommit,
                  @Param("status") String status, @Param("idempotencyKey") String idempotencyKey, @Param("snapshot") String snapshot,
                  @Param("requestedBy") String requestedBy, @Param("createdAt") Instant createdAt);

    @Insert("insert into pl_step_runs(id,run_id,pipeline_step_id,step_sequence_no,step_id,plugin_name,plugin_version,input_json,status) values(#{id},#{runId},#{pipelineStepId},#{sequenceNo},#{stepId},#{pluginName},#{pluginVersion},#{inputJson},#{status})")
    int createStepRun(@Param("id") String id, @Param("runId") String runId, @Param("pipelineStepId") String pipelineStepId,
                      @Param("sequenceNo") int sequenceNo, @Param("stepId") String stepId, @Param("pluginName") String pluginName,
                      @Param("pluginVersion") String pluginVersion, @Param("inputJson") String inputJson, @Param("status") String status);

    @Select("select id,tenant_id as tenantId,project_id as projectId,pipeline_id as pipelineId,pipeline_version_id as pipelineVersionId,repository_id as repositoryId,repository_version_no as repositoryVersionNo,source_branch as sourceBranch,source_commit as sourceCommit,status,idempotency_key as idempotencyKey,configuration_snapshot as configurationSnapshot,failure_code as failureCode,failure_message as failureMessage,requested_by as requestedBy,created_at as createdAt,started_at as startedAt,finished_at as finishedAt from pl_runs where id=#{runId}")
    RunRow findRun(@Param("runId") String runId);

    @Select("select id,tenant_id as tenantId,project_id as projectId,pipeline_id as pipelineId,pipeline_version_id as pipelineVersionId,repository_id as repositoryId,repository_version_no as repositoryVersionNo,source_branch as sourceBranch,source_commit as sourceCommit,status,idempotency_key as idempotencyKey,configuration_snapshot as configurationSnapshot,failure_code as failureCode,failure_message as failureMessage,requested_by as requestedBy,created_at as createdAt,started_at as startedAt,finished_at as finishedAt from pl_runs where project_id=#{projectId} and idempotency_key=#{idempotencyKey}")
    RunRow findByIdempotencyKey(@Param("projectId") String projectId, @Param("idempotencyKey") String idempotencyKey);

    @Select("select id,tenant_id as tenantId,project_id as projectId,pipeline_id as pipelineId,pipeline_version_id as pipelineVersionId,repository_id as repositoryId,repository_version_no as repositoryVersionNo,source_branch as sourceBranch,source_commit as sourceCommit,status,idempotency_key as idempotencyKey,configuration_snapshot as configurationSnapshot,failure_code as failureCode,failure_message as failureMessage,requested_by as requestedBy,created_at as createdAt,started_at as startedAt,finished_at as finishedAt from pl_runs where status='QUEUED' order by created_at limit #{limit}")
    List<RunRow> listQueued(@Param("limit") int limit);

    @Select("select id,run_id as runId,pipeline_step_id as pipelineStepId,step_sequence_no as sequenceNo,step_id as stepId,plugin_name as pluginName,plugin_version as pluginVersion,input_json as inputJson,status,output_json as outputJson,failure_code as failureCode,failure_message as failureMessage,started_at as startedAt,finished_at as finishedAt from pl_step_runs where run_id=#{runId} order by step_sequence_no")
    List<StepRunRow> listStepRuns(@Param("runId") String runId);

    @Update("update pl_runs set status=#{status},started_at=#{startedAt},finished_at=#{finishedAt},failure_code=#{failureCode},failure_message=#{failureMessage} where id=#{runId} and status=#{expectedStatus}")
    int updateRunStatus(@Param("runId") String runId, @Param("expectedStatus") String expectedStatus, @Param("status") String status,
                        @Param("startedAt") Instant startedAt, @Param("finishedAt") Instant finishedAt, @Param("failureCode") String failureCode,
                        @Param("failureMessage") String failureMessage);

    @Update("update pl_step_runs set status=#{status},started_at=#{startedAt},finished_at=#{finishedAt},output_json=#{outputJson},failure_code=#{failureCode},failure_message=#{failureMessage} where id=#{stepRunId} and status=#{expectedStatus}")
    int updateStepStatus(@Param("stepRunId") String stepRunId, @Param("expectedStatus") String expectedStatus, @Param("status") String status,
                         @Param("startedAt") Instant startedAt, @Param("finishedAt") Instant finishedAt, @Param("outputJson") String outputJson,
                         @Param("failureCode") String failureCode, @Param("failureMessage") String failureMessage);

    @Update("update pl_step_runs set status='SKIPPED',finished_at=#{finishedAt},failure_code=#{failureCode},failure_message=#{failureMessage} where run_id=#{runId} and status='PENDING'")
    int skipPending(@Param("runId") String runId, @Param("finishedAt") Instant finishedAt, @Param("failureCode") String failureCode,
                    @Param("failureMessage") String failureMessage);

    @Insert("insert into pl_step_logs(id,step_run_id,sequence_no,level,message,created_at) values(#{id},#{stepRunId},#{sequenceNo},#{level},#{message},#{createdAt})")
    int createLog(@Param("id") String id, @Param("stepRunId") String stepRunId, @Param("sequenceNo") int sequenceNo,
                  @Param("level") String level, @Param("message") String message, @Param("createdAt") Instant createdAt);

    @Select("select id,step_run_id as stepRunId,sequence_no as sequenceNo,level,message,created_at as createdAt from pl_step_logs where step_run_id=#{stepRunId} order by sequence_no")
    List<LogRow> listLogs(@Param("stepRunId") String stepRunId);

    record LogRow(String id, String stepRunId, int sequenceNo, String level, String message, Instant createdAt) { }
}

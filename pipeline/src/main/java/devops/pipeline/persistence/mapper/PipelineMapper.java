package devops.pipeline.persistence.mapper;

import devops.pipeline.dao.PipelineDao.PipelineRow;
import devops.pipeline.dao.PipelineDao.PipelineVersionRow;
import devops.pipeline.dao.PipelineDao.StepRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis 映射只处理流水线定义及不可变版本数据，不包含 YAML、授权或插件业务判断。 */
@Mapper
public interface PipelineMapper {
    @Insert("insert into pl_pipelines(id,tenant_id,project_id,name,description,enabled,current_version_no,created_by,created_at,updated_at) "
            + "values(#{id},#{tenantId},#{projectId},#{name},#{description},#{enabled},#{versionNo},#{createdBy},#{createdAt},#{createdAt})")
    int createPipeline(@Param("id") String id, @Param("tenantId") String tenantId, @Param("projectId") String projectId,
                       @Param("name") String name, @Param("description") String description, @Param("enabled") boolean enabled,
                       @Param("versionNo") int versionNo, @Param("createdBy") String createdBy, @Param("createdAt") Instant createdAt);

    @Insert("insert into pl_pipeline_versions(id,pipeline_id,version_no,yaml_content,content_sha256,repository_id,repository_version_no,source_default_branch,created_by,created_at) "
            + "values(#{id},#{pipelineId},#{versionNo},#{yamlContent},#{sha256},#{repositoryId},#{repositoryVersionNo},#{defaultBranch},#{createdBy},#{createdAt})")
    int createVersion(@Param("id") String id, @Param("pipelineId") String pipelineId, @Param("versionNo") int versionNo,
                      @Param("yamlContent") String yamlContent, @Param("sha256") String sha256, @Param("repositoryId") String repositoryId,
                      @Param("repositoryVersionNo") int repositoryVersionNo, @Param("defaultBranch") String defaultBranch,
                      @Param("createdBy") String createdBy, @Param("createdAt") Instant createdAt);

    @Insert("insert into pl_pipeline_steps(id,pipeline_version_id,stage_name,stage_sequence_no,step_id,step_sequence_no,plugin_name,plugin_version,input_json,created_at) "
            + "values(#{id},#{pipelineVersionId},#{stageName},#{stageSequenceNo},#{stepId},#{stepSequenceNo},#{pluginName},#{pluginVersion},#{inputJson},#{createdAt})")
    int createStep(@Param("id") String id, @Param("pipelineVersionId") String pipelineVersionId, @Param("stageName") String stageName,
                   @Param("stageSequenceNo") int stageSequenceNo, @Param("stepId") String stepId, @Param("stepSequenceNo") int stepSequenceNo,
                   @Param("pluginName") String pluginName, @Param("pluginVersion") String pluginVersion, @Param("inputJson") String inputJson,
                   @Param("createdAt") Instant createdAt);

    @Select("select id,tenant_id as tenantId,project_id as projectId,name,description,enabled,current_version_no as currentVersionNo,created_by as createdBy,created_at as createdAt,updated_at as updatedAt from pl_pipelines where id=#{pipelineId}")
    PipelineRow findPipeline(@Param("pipelineId") String pipelineId);

    @Select("select id,tenant_id as tenantId,project_id as projectId,name,description,enabled,current_version_no as currentVersionNo,created_by as createdBy,created_at as createdAt,updated_at as updatedAt from pl_pipelines where project_id=#{projectId} order by created_at desc")
    List<PipelineRow> listPipelines(@Param("projectId") String projectId);

    @Select("select id,pipeline_id as pipelineId,version_no as versionNo,yaml_content as yamlContent,content_sha256 as contentSha256,repository_id as repositoryId,repository_version_no as repositoryVersionNo,source_default_branch as sourceDefaultBranch,created_by as createdBy,created_at as createdAt from pl_pipeline_versions where id=#{versionId}")
    PipelineVersionRow findVersion(@Param("versionId") String versionId);

    @Select("select id,pipeline_id as pipelineId,version_no as versionNo,yaml_content as yamlContent,content_sha256 as contentSha256,repository_id as repositoryId,repository_version_no as repositoryVersionNo,source_default_branch as sourceDefaultBranch,created_by as createdBy,created_at as createdAt from pl_pipeline_versions where pipeline_id=#{pipelineId} order by version_no desc")
    List<PipelineVersionRow> listVersions(@Param("pipelineId") String pipelineId);

    @Select("select id,pipeline_version_id as pipelineVersionId,stage_name as stageName,stage_sequence_no as stageSequenceNo,step_id as stepId,step_sequence_no as stepSequenceNo,plugin_name as pluginName,plugin_version as pluginVersion,input_json as inputJson,created_at as createdAt from pl_pipeline_steps where pipeline_version_id=#{versionId} order by step_sequence_no")
    List<StepRow> listSteps(@Param("versionId") String versionId);

    @Update("update pl_pipelines set name=#{name},description=#{description},current_version_no=current_version_no+1,updated_at=#{updatedAt} where id=#{pipelineId} and current_version_no=#{expectedVersion}")
    int updatePipeline(@Param("pipelineId") String pipelineId, @Param("expectedVersion") int expectedVersion,
                       @Param("name") String name, @Param("description") String description, @Param("updatedAt") Instant updatedAt);

    @Update("update pl_pipelines set enabled=#{enabled},updated_at=#{updatedAt} where id=#{pipelineId} and current_version_no=#{expectedVersion}")
    int setEnabled(@Param("pipelineId") String pipelineId, @Param("expectedVersion") int expectedVersion,
                   @Param("enabled") boolean enabled, @Param("updatedAt") Instant updatedAt);
}

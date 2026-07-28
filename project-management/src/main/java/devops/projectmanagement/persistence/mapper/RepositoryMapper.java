package devops.projectmanagement.persistence.mapper;

import devops.projectmanagement.dao.RepositoryDao.RepositoryRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 仓库 Mapper 只保存公开地址和健康摘要，绝不保存 GitHub 访问令牌。 */
@Mapper
public interface RepositoryMapper {
    @Insert("insert into pm_repositories(id,tenant_id,project_id,canonical_url,default_branch,current_version_no,connection_status,last_checked_at,last_error_code,created_by,created_at,updated_at) "
            + "values(#{id},#{tenantId},#{projectId},#{canonicalUrl},#{defaultBranch},1,#{status},#{checkedAt},#{errorCode},#{createdBy},#{now},#{now})")
    int create(@Param("id") String id, @Param("tenantId") String tenantId, @Param("projectId") String projectId,
               @Param("canonicalUrl") String canonicalUrl, @Param("defaultBranch") String defaultBranch,
               @Param("status") String status, @Param("checkedAt") Instant checkedAt, @Param("errorCode") String errorCode,
               @Param("createdBy") String createdBy, @Param("now") Instant now);

    @Insert("insert into pm_repository_versions(id,repository_id,version_no,canonical_url,default_branch,created_by,created_at) "
            + "values(#{id},#{repositoryId},#{versionNo},#{canonicalUrl},#{defaultBranch},#{createdBy},#{now})")
    int createVersion(@Param("id") String id, @Param("repositoryId") String repositoryId, @Param("versionNo") int versionNo,
                      @Param("canonicalUrl") String canonicalUrl, @Param("defaultBranch") String defaultBranch,
                      @Param("createdBy") String createdBy, @Param("now") Instant now);

    @Select("select id,tenant_id as tenantId,project_id as projectId,canonical_url as canonicalUrl,default_branch as defaultBranch,"
            + "current_version_no as currentVersionNo,connection_status as connectionStatus,last_checked_at as lastCheckedAt,"
            + "last_error_code as lastErrorCode,created_by as createdBy,created_at as createdAt,updated_at as updatedAt from pm_repositories where id=#{repositoryId}")
    RepositoryRow findById(@Param("repositoryId") String repositoryId);

    @Select("select id,tenant_id as tenantId,project_id as projectId,canonical_url as canonicalUrl,default_branch as defaultBranch,"
            + "current_version_no as currentVersionNo,connection_status as connectionStatus,last_checked_at as lastCheckedAt,"
            + "last_error_code as lastErrorCode,created_by as createdBy,created_at as createdAt,updated_at as updatedAt from pm_repositories where project_id=#{projectId} order by created_at desc")
    List<RepositoryRow> listByProject(@Param("projectId") String projectId);

    @Select("select count(*) from pm_repositories where project_id=#{projectId}")
    int countByProject(@Param("projectId") String projectId);

    @Update("update pm_repositories set canonical_url=#{canonicalUrl},default_branch=#{defaultBranch},current_version_no=current_version_no+1,"
            + "connection_status=#{status},last_checked_at=#{checkedAt},last_error_code=#{errorCode},updated_at=#{now} "
            + "where id=#{repositoryId} and current_version_no=#{expectedVersion}")
    int update(@Param("repositoryId") String repositoryId, @Param("expectedVersion") int expectedVersion,
               @Param("canonicalUrl") String canonicalUrl, @Param("defaultBranch") String defaultBranch,
               @Param("status") String status, @Param("checkedAt") Instant checkedAt, @Param("errorCode") String errorCode,
               @Param("now") Instant now);

    @Update("update pm_repositories set connection_status=#{status},last_checked_at=#{checkedAt},last_error_code=#{errorCode},updated_at=#{now} where id=#{repositoryId}")
    int updateHealth(@Param("repositoryId") String repositoryId, @Param("status") String status,
                     @Param("checkedAt") Instant checkedAt, @Param("errorCode") String errorCode, @Param("now") Instant now);

    @Delete("delete from pm_repositories where id=#{repositoryId}")
    int delete(@Param("repositoryId") String repositoryId);
}

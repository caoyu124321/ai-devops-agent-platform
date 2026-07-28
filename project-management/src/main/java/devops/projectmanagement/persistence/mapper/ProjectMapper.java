package devops.projectmanagement.persistence.mapper;

import devops.projectmanagement.dao.ProjectDao.ProjectRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis 映射只表达项目表读写，乐观锁由更新条件确保旧版本不会覆盖新版本。 */
@Mapper
public interface ProjectMapper {
    @Insert("insert into pm_projects(id,tenant_id,name,description,current_version_no,created_by,created_at,updated_at) "
            + "values(#{id},#{tenantId},#{name},#{description},#{versionNo},#{createdBy},#{createdAt},#{createdAt})")
    int create(@Param("id") String id, @Param("tenantId") String tenantId, @Param("name") String name,
               @Param("description") String description, @Param("versionNo") int versionNo,
               @Param("createdBy") String createdBy, @Param("createdAt") Instant createdAt);

    @Insert("insert into pm_project_versions(id,project_id,version_no,name,description,created_by,created_at) "
            + "values(#{id},#{projectId},#{versionNo},#{name},#{description},#{createdBy},#{createdAt})")
    int createVersion(@Param("id") String id, @Param("projectId") String projectId, @Param("versionNo") int versionNo,
                      @Param("name") String name, @Param("description") String description,
                      @Param("createdBy") String createdBy, @Param("createdAt") Instant createdAt);

    @Select("select id,tenant_id as tenantId,name,description,current_version_no as currentVersionNo,created_by as createdBy,"
            + "created_at as createdAt,updated_at as updatedAt from pm_projects where id=#{projectId}")
    ProjectRow findById(@Param("projectId") String projectId);

    @Select("select id,tenant_id as tenantId,name,description,current_version_no as currentVersionNo,created_by as createdBy,"
            + "created_at as createdAt,updated_at as updatedAt from pm_projects where tenant_id=#{tenantId} order by created_at desc")
    List<ProjectRow> listByTenant(@Param("tenantId") String tenantId);

    @Update("update pm_projects set name=#{name},description=#{description},current_version_no=current_version_no+1,"
            + "updated_at=#{updatedAt} where id=#{projectId} and current_version_no=#{expectedVersion}")
    int update(@Param("projectId") String projectId, @Param("expectedVersion") int expectedVersion,
               @Param("name") String name, @Param("description") String description, @Param("updatedAt") Instant updatedAt);

    @Delete("delete from pm_projects where id=#{projectId}")
    int delete(@Param("projectId") String projectId);
}

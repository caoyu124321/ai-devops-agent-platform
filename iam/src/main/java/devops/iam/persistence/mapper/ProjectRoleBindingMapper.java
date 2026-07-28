package devops.iam.persistence.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 项目角色绑定持久化映射；项目 ID 只是调用方提供的抽象标识。 */
@Mapper
public interface ProjectRoleBindingMapper {
    @Insert("insert into iam_project_role_bindings(id,tenant_id,member_id,project_id,role_code,created_by,created_at,updated_at) values(#{id},#{tenantId},#{memberId},#{projectId},#{roleCode},#{createdBy},#{now},#{now}) on duplicate key update role_code=values(role_code),created_by=values(created_by),updated_at=values(updated_at)")
    int create(@Param("id") String id, @Param("tenantId") String tenantId, @Param("memberId") String memberId,
               @Param("projectId") String projectId, @Param("roleCode") String roleCode,
               @Param("createdBy") String createdBy, @Param("now") Instant now);

    @Select("select role_code from iam_project_role_bindings where tenant_id=#{tenantId} and member_id=#{memberId} and project_id=#{projectId}")
    String findRole(@Param("tenantId") String tenantId, @Param("memberId") String memberId,
                    @Param("projectId") String projectId);

    @Select("select project_id as projectId, role_code as roleCode from iam_project_role_bindings where tenant_id=#{tenantId} and member_id=#{memberId} order by project_id")
    List<ProjectRoleBindingRow> listByMember(@Param("tenantId") String tenantId, @Param("memberId") String memberId);

    @Delete("delete from iam_project_role_bindings where tenant_id=#{tenantId} and member_id=#{memberId} and project_id=#{projectId}")
    int deleteByMemberAndProject(@Param("tenantId") String tenantId, @Param("memberId") String memberId,
                                 @Param("projectId") String projectId);

    @Delete("delete from iam_project_role_bindings where member_id=#{memberId}")
    int deleteByMember(@Param("memberId") String memberId);

    @Delete("delete from iam_project_role_bindings where tenant_id=#{tenantId} and project_id=#{projectId}")
    int deleteByProject(@Param("tenantId") String tenantId, @Param("projectId") String projectId);

    record ProjectRoleBindingRow(String projectId, String roleCode) { }
}

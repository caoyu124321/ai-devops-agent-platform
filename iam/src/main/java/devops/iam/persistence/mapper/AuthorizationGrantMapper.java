package devops.iam.persistence.mapper;

import devops.iam.dao.AuthorizationGrantDao;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 抽象资源范围授权项的 MyBatis 映射。 */
@Mapper
public interface AuthorizationGrantMapper {
    String COLUMNS = "id,tenant_id as tenantId,member_id as memberId,resource_type as resourceType,resource_id as resourceId,action_code as actionCode,environment_level as environmentLevel,effect,created_by as createdBy,created_at as createdAt";
    @Select("select " + COLUMNS + " from iam_authorization_grants where tenant_id=#{tenantId} order by created_at desc") List<AuthorizationGrantDao.GrantRow> listByTenant(@Param("tenantId") String tenantId);
    @Select("select " + COLUMNS + " from iam_authorization_grants where tenant_id=#{tenantId} and member_id=#{memberId} and resource_type in (#{resourceType}, '*') and resource_id in (#{resourceId}, '*') and action_code in (#{actionCode}, '*') and (environment_level is null or environment_level=#{environmentLevel}) and effect='ALLOW'") List<AuthorizationGrantDao.GrantRow> findMatches(@Param("tenantId") String tenantId,@Param("memberId") String memberId,@Param("resourceType") String resourceType,@Param("resourceId") String resourceId,@Param("actionCode") String actionCode,@Param("environmentLevel") String environmentLevel);
    @Select("select " + COLUMNS + " from iam_authorization_grants where id=#{id}") AuthorizationGrantDao.GrantRow findById(@Param("id") String id);
    @Insert("insert into iam_authorization_grants(id,tenant_id,member_id,resource_type,resource_id,action_code,environment_level,effect,created_by,created_at,updated_at) values(#{id},#{tenantId},#{memberId},#{resourceType},#{resourceId},#{actionCode},#{environmentLevel},'ALLOW',#{creatorId},#{now},#{now})") int create(@Param("id") String id,@Param("tenantId") String tenantId,@Param("memberId") String memberId,@Param("resourceType") String resourceType,@Param("resourceId") String resourceId,@Param("actionCode") String actionCode,@Param("environmentLevel") String environmentLevel,@Param("creatorId") String creatorId,@Param("now") Instant now);
    @Delete("delete from iam_authorization_grants where id=#{id}") int delete(@Param("id") String id);
}

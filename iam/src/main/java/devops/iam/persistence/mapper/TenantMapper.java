package devops.iam.persistence.mapper;

import devops.iam.dao.TenantDao;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 租户、成员和邀请 MyBatis 映射。 */
@Mapper
public interface TenantMapper {
    @Insert("insert into iam_tenants(id,name,created_by,created_at,updated_at) values(#{id},#{name},#{creatorId},#{now},#{now})")
    int createTenant(@Param("id") String id, @Param("name") String name, @Param("creatorId") String creatorId, @Param("now") Instant now);
    @Insert("insert into iam_tenant_members(id,tenant_id,user_id,role_code,joined_at,updated_at) values(#{id},#{tenantId},#{userId},#{roleCode},#{now},#{now})")
    int createMember(@Param("id") String id, @Param("tenantId") String tenantId, @Param("userId") String userId, @Param("roleCode") String roleCode, @Param("now") Instant now);
    @Select("select t.id,t.name,t.created_by as createdBy,t.created_at as createdAt,m.role_code as roleCode from iam_tenants t join iam_tenant_members m on m.tenant_id=t.id where m.user_id=#{userId} order by t.created_at desc")
    List<TenantDao.TenantRow> findTenantsByUser(@Param("userId") String userId);
    @Select("select id,tenant_id as tenantId,user_id as userId,role_code as roleCode,joined_at as joinedAt from iam_tenant_members where tenant_id=#{tenantId} and user_id=#{userId}")
    TenantDao.MemberRow findMember(@Param("tenantId") String tenantId, @Param("userId") String userId);
    @Select("select id,tenant_id as tenantId,user_id as userId,role_code as roleCode,joined_at as joinedAt from iam_tenant_members where tenant_id=#{tenantId} and id=#{memberId}")
    TenantDao.MemberRow findMemberById(@Param("tenantId") String tenantId, @Param("memberId") String memberId);
    @Select("select id,tenant_id as tenantId,user_id as userId,role_code as roleCode,joined_at as joinedAt from iam_tenant_members where tenant_id=#{tenantId} order by joined_at")
    List<TenantDao.MemberRow> listMembers(@Param("tenantId") String tenantId);
    @Select("select count(*) from iam_tenant_members where tenant_id=#{tenantId} and role_code='TENANT_ADMIN'") int countAdministrators(@Param("tenantId") String tenantId);
    @Update("update iam_tenant_members set role_code=#{roleCode},updated_at=#{now} where id=#{memberId}") int updateMemberRole(@Param("memberId") String memberId,@Param("roleCode") String roleCode,@Param("now") Instant now);
    @Delete("delete from iam_tenant_members where id=#{memberId}") int deleteMember(@Param("memberId") String memberId);
    @Select("select id,username,email from iam_users where username=#{login} or email=#{login} limit 1") TenantDao.UserRow findUserByLogin(@Param("login") String login);
    @Delete("delete from iam_invitations where status='PENDING' and expires_at<=#{now}") int deleteExpiredInvitations(@Param("now") Instant now);
    @Select("select id,tenant_id as tenantId,invited_user_id as invitedUserId,role_code as roleCode,invited_by as invitedBy,created_at as createdAt,expires_at as expiresAt,status from iam_invitations where tenant_id=#{tenantId} and invited_user_id=#{userId} and status='PENDING' and expires_at>#{now} limit 1") TenantDao.InvitationRow findPendingInvitation(@Param("tenantId") String tenantId,@Param("userId") String userId,@Param("now") Instant now);
    @Insert("insert into iam_invitations(id,tenant_id,invited_user_id,role_code,invited_by,created_at,expires_at,status) values(#{id},#{tenantId},#{invitedUserId},#{roleCode},#{invitedBy},#{now},#{expiresAt},'PENDING')") int createInvitation(@Param("id") String id,@Param("tenantId") String tenantId,@Param("invitedUserId") String invitedUserId,@Param("roleCode") String roleCode,@Param("invitedBy") String invitedBy,@Param("now") Instant now,@Param("expiresAt") Instant expiresAt);
    @Select("select id,tenant_id as tenantId,invited_user_id as invitedUserId,role_code as roleCode,invited_by as invitedBy,created_at as createdAt,expires_at as expiresAt,status from iam_invitations where id=#{id}") TenantDao.InvitationRow findInvitation(@Param("id") String id);
    @Update("update iam_invitations set status=#{status},resolved_at=#{now} where id=#{id} and status='PENDING'") int resolveInvitation(@Param("id") String id,@Param("status") String status,@Param("now") Instant now);
}

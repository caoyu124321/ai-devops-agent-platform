package devops.iam.persistence.mapper;

import devops.iam.dao.RegistrationLinkDao;
import devops.iam.identity.RegistrationLinkStatus;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 注册链接 SQL 集中在持久化边界，令牌原文不会进入表或查询结果。 */
@Mapper
public interface RegistrationLinkMapper {
    @Insert("insert into iam_registration_links(id,token_hash,status,created_at,expires_at) values(#{id},#{tokenHash},#{status},#{createdAt},#{expiresAt})")
    int create(@Param("id") String id, @Param("tokenHash") String tokenHash,
               @Param("status") RegistrationLinkStatus status, @Param("createdAt") Instant createdAt,
               @Param("expiresAt") Instant expiresAt);

    @Select("select id,token_hash as tokenHash,status,created_at as createdAt,expires_at as expiresAt,completed_at as completedAt,registered_user_id as registeredUserId from iam_registration_links where id=#{id}")
    RegistrationLinkDao.LinkRow findById(@Param("id") String id);

    @Select("select id,token_hash as tokenHash,status,created_at as createdAt,expires_at as expiresAt,completed_at as completedAt,registered_user_id as registeredUserId from iam_registration_links where id=#{id} for update")
    RegistrationLinkDao.LinkRow findByIdForUpdate(@Param("id") String id);

    @Update("update iam_registration_links set status=#{status} where id=#{id} and status=#{expectedStatus}")
    int updateStatus(@Param("id") String id, @Param("expectedStatus") RegistrationLinkStatus expectedStatus,
                     @Param("status") RegistrationLinkStatus status);

    @Update("update iam_registration_links set status=#{status},completed_at=#{completedAt},registered_user_id=#{userId} where id=#{id} and status='PENDING'")
    int complete(@Param("id") String id, @Param("status") RegistrationLinkStatus status,
                 @Param("completedAt") Instant completedAt, @Param("userId") String userId);
}

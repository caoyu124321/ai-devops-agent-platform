package devops.iam.persistence.mapper;

import devops.iam.dao.LoginLinkDao;
import devops.iam.identity.LoginLinkStatus;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 登录链接 SQL 只处理令牌哈希，原始平台会话令牌不进入持久化层。 */
@Mapper
public interface LoginLinkMapper {
    @Insert("insert into iam_login_links(id,token_hash,session_token_hash,status,created_at,expires_at) values(#{id},#{tokenHash},#{sessionTokenHash},#{status},#{createdAt},#{expiresAt})")
    int create(@Param("id") String id, @Param("tokenHash") String tokenHash, @Param("sessionTokenHash") String sessionTokenHash,
               @Param("status") LoginLinkStatus status, @Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt);

    @Select("select id,token_hash as tokenHash,session_token_hash as sessionTokenHash,status,created_at as createdAt,expires_at as expiresAt,completed_at as completedAt,session_expires_at as sessionExpiresAt,user_id as userId from iam_login_links where id=#{id}")
    LoginLinkDao.LinkRow findById(@Param("id") String id);

    @Select("select id,token_hash as tokenHash,session_token_hash as sessionTokenHash,status,created_at as createdAt,expires_at as expiresAt,completed_at as completedAt,session_expires_at as sessionExpiresAt,user_id as userId from iam_login_links where id=#{id} for update")
    LoginLinkDao.LinkRow findByIdForUpdate(@Param("id") String id);

    @Update("update iam_login_links set status=#{status} where id=#{id} and status='PENDING'")
    int expire(@Param("id") String id, @Param("status") LoginLinkStatus status);

    @Update("update iam_login_links set status=#{status},completed_at=#{completedAt},session_expires_at=#{sessionExpiresAt},user_id=#{userId} where id=#{id} and status='PENDING'")
    int complete(@Param("id") String id, @Param("status") LoginLinkStatus status, @Param("completedAt") Instant completedAt,
                 @Param("sessionExpiresAt") Instant sessionExpiresAt, @Param("userId") String userId);
}

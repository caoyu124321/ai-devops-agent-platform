package devops.iam.persistence.mapper;

import devops.iam.dao.IdentityDao;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 身份、会话与登录锁定的 MyBatis 映射，SQL 集中在持久化边界。 */
@Mapper
public interface IdentityMapper {
    @Select("select id,username,email,password_hash as passwordHash from iam_users where username=#{login} or email=#{login} limit 1")
    IdentityDao.UserRow findUser(@Param("login") String login);

    @Select("select id,username,email,password_hash as passwordHash from iam_users where id=#{userId}")
    IdentityDao.UserRow findUserById(@Param("userId") String userId);

    @Insert("insert into iam_users(id,username,email,password_hash,created_at,updated_at) values(#{id},#{username},#{email},#{passwordHash},#{now},#{now})")
    int createUser(@Param("id") String id, @Param("username") String username, @Param("email") String email,
                   @Param("passwordHash") String passwordHash, @Param("now") Instant now);

    @Select("select failed_count as failedCount,locked_until as lockedUntil from iam_login_locks where user_id=#{userId}")
    IdentityDao.LockRow findLock(@Param("userId") String userId);

    @Update("update iam_login_locks set failed_count=#{count},last_failed_at=#{now},locked_until=#{lockedUntil},updated_at=#{now} where user_id=#{userId}")
    int updateFailure(@Param("userId") String userId, @Param("count") int count, @Param("now") Instant now,
                      @Param("lockedUntil") Instant lockedUntil);

    @Insert("insert into iam_login_locks(user_id,failed_count,last_failed_at,locked_until,updated_at) values(#{userId},#{count},#{now},#{lockedUntil},#{now})")
    int insertFailure(@Param("userId") String userId, @Param("count") int count, @Param("now") Instant now,
                      @Param("lockedUntil") Instant lockedUntil);

    @Delete("delete from iam_login_locks where user_id=#{userId}")
    int clearFailures(@Param("userId") String userId);

    @Insert("insert into iam_sessions(id,user_id,token_hash,issued_at,expires_at,client_summary) values(#{id},#{userId},#{tokenHash},#{issuedAt},#{expiresAt},#{clientSummary})")
    int createSession(@Param("id") String id, @Param("userId") String userId, @Param("tokenHash") String tokenHash,
                      @Param("issuedAt") Instant issuedAt, @Param("expiresAt") Instant expiresAt,
                      @Param("clientSummary") String clientSummary);

    @Select("select id,user_id as userId,expires_at as expiresAt from iam_sessions where token_hash=#{tokenHash} and revoked_at is null and expires_at>#{now}")
    IdentityDao.SessionRow findActiveSession(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Update("update iam_sessions set revoked_at=coalesce(revoked_at,#{now}),revocation_reason=coalesce(revocation_reason,#{reason}) where id=#{id}")
    int revokeSession(@Param("id") String id, @Param("now") Instant now, @Param("reason") String reason);

    @Update("update iam_sessions set revoked_at=#{now},revocation_reason='PASSWORD_CHANGED' where user_id=#{userId} and revoked_at is null")
    int revokeAll(@Param("userId") String userId, @Param("now") Instant now);

    @Update("update iam_users set password_hash=#{passwordHash},updated_at=#{now} where id=#{userId}")
    int updatePassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash, @Param("now") Instant now);
}

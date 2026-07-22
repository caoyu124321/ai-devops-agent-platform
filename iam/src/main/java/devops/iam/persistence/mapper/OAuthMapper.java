package devops.iam.persistence.mapper;

import devops.iam.dao.OAuthDao;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** OAuth SQL 集中在 Mapper，所有 Token 查询均以哈希作为条件。 */
@Mapper
public interface OAuthMapper {
    @Insert("insert into iam_oauth_clients(id,client_name,redirect_uris,status,created_at,updated_at) values(#{id},#{clientName},#{redirectUris},#{status},#{createdAt},#{updatedAt})")
    int createClient(OAuthDao.ClientRow row);

    @Select("select id,client_name as clientName,redirect_uris as redirectUris,status,created_at as createdAt,updated_at as updatedAt from iam_oauth_clients where id=#{id}")
    OAuthDao.ClientRow findClient(@Param("id") String id);

    @Insert("insert into iam_oauth_browser_sessions(id,token_hash,user_id,issued_at,expires_at) values(#{id},#{tokenHash},#{userId},#{issuedAt},#{expiresAt})")
    int createBrowserSession(OAuthDao.BrowserSessionRow row);

    @Select("select u.id as userId,u.username,u.email from iam_oauth_browser_sessions s join iam_users u on u.id=s.user_id where s.token_hash=#{tokenHash} and s.revoked_at is null and s.expires_at>#{now}")
    OAuthDao.BrowserPrincipalRow findBrowserPrincipal(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Insert("insert into iam_oauth_grants(id,user_id,client_id,audience,scopes,absolute_expires_at,last_used_at,created_at) values(#{id},#{userId},#{clientId},#{audience},#{scopes},#{absoluteExpiresAt},#{lastUsedAt},#{createdAt})")
    int createGrant(OAuthDao.GrantRow row);

    @Insert("insert into iam_oauth_authorization_codes(id,code_hash,grant_id,client_id,redirect_uri,code_challenge,expires_at,created_at) values(#{id},#{codeHash},#{grantId},#{clientId},#{redirectUri},#{codeChallenge},#{expiresAt},#{createdAt})")
    int createAuthorizationCode(OAuthDao.AuthorizationCodeRow row);

    @Select("select id,code_hash as codeHash,grant_id as grantId,client_id as clientId,redirect_uri as redirectUri,code_challenge as codeChallenge,expires_at as expiresAt,consumed_at as consumedAt,created_at as createdAt from iam_oauth_authorization_codes where code_hash=#{hash} for update")
    OAuthDao.AuthorizationCodeRow findAuthorizationCodeForUpdate(@Param("hash") String hash);

    @Update("update iam_oauth_authorization_codes set consumed_at=#{now} where id=#{id} and consumed_at is null and expires_at>#{now}")
    int consumeAuthorizationCode(@Param("id") String id, @Param("now") Instant now);

    @Select("select id,user_id as userId,client_id as clientId,audience,scopes,absolute_expires_at as absoluteExpiresAt,last_used_at as lastUsedAt,revoked_at as revokedAt,revocation_reason as revocationReason,created_at as createdAt from iam_oauth_grants where id=#{id} for update")
    OAuthDao.GrantRow findGrantForUpdate(@Param("id") String id);

    @Update("update iam_oauth_grants set last_used_at=#{now} where id=#{id}")
    int updateGrantUsage(@Param("id") String id, @Param("now") Instant now);

    @Insert("insert into iam_oauth_access_tokens(id,token_hash,grant_id,user_id,client_id,audience,scopes,issued_at,expires_at) values(#{id},#{tokenHash},#{grantId},#{userId},#{clientId},#{audience},#{scopes},#{issuedAt},#{expiresAt})")
    int createAccessToken(OAuthDao.AccessTokenRow row);

    @Insert("insert into iam_oauth_refresh_tokens(id,token_hash,grant_id,parent_token_id,status,issued_at) values(#{id},#{tokenHash},#{grantId},#{parentTokenId},#{status},#{issuedAt})")
    int createRefreshToken(OAuthDao.RefreshTokenRow row);

    @Select("select id,token_hash as tokenHash,grant_id as grantId,parent_token_id as parentTokenId,status,issued_at as issuedAt,rotated_at as rotatedAt from iam_oauth_refresh_tokens where token_hash=#{hash} for update")
    OAuthDao.RefreshTokenRow findRefreshTokenForUpdate(@Param("hash") String hash);

    @Update("update iam_oauth_refresh_tokens set status='ROTATED',rotated_at=#{now} where id=#{id} and status='ACTIVE'")
    int rotateRefreshToken(@Param("id") String id, @Param("now") Instant now);

    @Select("select t.id as tokenId,t.grant_id as grantId,t.user_id as userId,t.client_id as clientId,t.audience,t.scopes,t.issued_at as issuedAt,t.expires_at as expiresAt,u.username,u.email from iam_oauth_access_tokens t join iam_users u on u.id=t.user_id join iam_oauth_grants g on g.id=t.grant_id where t.token_hash=#{hash} and t.revoked_at is null and t.expires_at>#{now} and g.revoked_at is null and g.absolute_expires_at>#{now}")
    OAuthDao.AccessPrincipalRow findAccessPrincipal(@Param("hash") String hash, @Param("now") Instant now);

    @Update("update iam_oauth_grants set revoked_at=coalesce(revoked_at,#{now}),revocation_reason=coalesce(revocation_reason,#{reason}) where id=#{grantId}")
    int revokeGrant(@Param("grantId") String grantId, @Param("now") Instant now, @Param("reason") String reason);

    @Update("update iam_oauth_access_tokens set revoked_at=coalesce(revoked_at,#{now}),revocation_reason=coalesce(revocation_reason,#{reason}) where grant_id=#{grantId} and revoked_at is null")
    int revokeGrantAccessTokens(@Param("grantId") String grantId, @Param("now") Instant now, @Param("reason") String reason);

    @Update("update iam_oauth_refresh_tokens set status='REVOKED' where grant_id=#{grantId} and status<>'REVOKED'")
    int revokeGrantRefreshTokens(@Param("grantId") String grantId);

    default void revokeGrantTokens(String grantId, Instant now, String reason) {
        revokeGrantAccessTokens(grantId, now, reason);
        revokeGrantRefreshTokens(grantId);
    }

    @Update("update iam_oauth_grants set revoked_at=coalesce(revoked_at,#{now}),revocation_reason=coalesce(revocation_reason,#{reason}) where user_id=#{userId} and revoked_at is null")
    int revokeUserGrants(@Param("userId") String userId, @Param("now") Instant now, @Param("reason") String reason);

    @Update("update iam_oauth_access_tokens set revoked_at=coalesce(revoked_at,#{now}),revocation_reason=coalesce(revocation_reason,#{reason}) where user_id=#{userId} and revoked_at is null")
    int revokeUserTokens(@Param("userId") String userId, @Param("now") Instant now, @Param("reason") String reason);

    @Update("update iam_oauth_browser_sessions set revoked_at=coalesce(revoked_at,#{now}) where user_id=#{userId} and revoked_at is null")
    int revokeUserBrowserSessions(@Param("userId") String userId, @Param("now") Instant now);

    @Update("update iam_oauth_grants set revoked_at=coalesce(revoked_at,#{now}),revocation_reason=coalesce(revocation_reason,#{reason}) where client_id=#{clientId} and revoked_at is null")
    int revokeClientGrants(@Param("clientId") String clientId, @Param("now") Instant now, @Param("reason") String reason);

    @Update("update iam_oauth_access_tokens set revoked_at=coalesce(revoked_at,#{now}),revocation_reason=coalesce(revocation_reason,#{reason}) where client_id=#{clientId} and revoked_at is null")
    int revokeClientTokens(@Param("clientId") String clientId, @Param("now") Instant now, @Param("reason") String reason);
}

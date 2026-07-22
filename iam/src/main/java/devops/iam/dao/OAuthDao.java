package devops.iam.dao;

import devops.iam.persistence.mapper.OAuthMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** OAuth 持久化门面只处理哈希和生命周期状态，服务层永远不需要接触 MyBatis SQL。 */
@Repository
public class OAuthDao {
    private final OAuthMapper mapper;

    public OAuthDao(OAuthMapper mapper) {
        this.mapper = mapper;
    }

    public void createClient(ClientRow row) { mapper.createClient(row); }

    public Optional<ClientRow> findClient(String id) { return Optional.ofNullable(mapper.findClient(id)); }

    public void createBrowserSession(BrowserSessionRow row) { mapper.createBrowserSession(row); }

    public Optional<BrowserPrincipalRow> findBrowserPrincipal(String tokenHash, Instant now) {
        return Optional.ofNullable(mapper.findBrowserPrincipal(tokenHash, now));
    }

    public void createGrant(GrantRow row) { mapper.createGrant(row); }

    public void createAuthorizationCode(AuthorizationCodeRow row) { mapper.createAuthorizationCode(row); }

    public Optional<AuthorizationCodeRow> findAuthorizationCodeForUpdate(String hash) {
        return Optional.ofNullable(mapper.findAuthorizationCodeForUpdate(hash));
    }

    public boolean consumeAuthorizationCode(String id, Instant now) { return mapper.consumeAuthorizationCode(id, now) == 1; }

    public Optional<GrantRow> findGrantForUpdate(String id) { return Optional.ofNullable(mapper.findGrantForUpdate(id)); }

    public void updateGrantUsage(String id, Instant now) { mapper.updateGrantUsage(id, now); }

    public void createAccessToken(AccessTokenRow row) { mapper.createAccessToken(row); }

    public void createRefreshToken(RefreshTokenRow row) { mapper.createRefreshToken(row); }

    public Optional<RefreshTokenRow> findRefreshTokenForUpdate(String hash) {
        return Optional.ofNullable(mapper.findRefreshTokenForUpdate(hash));
    }

    public boolean rotateRefreshToken(String id, Instant now) { return mapper.rotateRefreshToken(id, now) == 1; }

    public Optional<AccessPrincipalRow> findAccessPrincipal(String hash, Instant now) {
        return Optional.ofNullable(mapper.findAccessPrincipal(hash, now));
    }

    public void revokeGrant(String grantId, Instant now, String reason) { mapper.revokeGrant(grantId, now, reason); }

    public void revokeGrantTokens(String grantId, Instant now, String reason) { mapper.revokeGrantTokens(grantId, now, reason); }

    public void revokeUser(String userId, Instant now, String reason) {
        mapper.revokeUserGrants(userId, now, reason);
        mapper.revokeUserTokens(userId, now, reason);
        mapper.revokeUserBrowserSessions(userId, now);
    }

    public void revokeClient(String clientId, Instant now, String reason) {
        mapper.revokeClientGrants(clientId, now, reason);
        mapper.revokeClientTokens(clientId, now, reason);
    }

    public record ClientRow(String id, String clientName, String redirectUris, String status, Instant createdAt, Instant updatedAt) { }

    public record BrowserSessionRow(String id, String tokenHash, String userId, Instant issuedAt, Instant expiresAt) { }

    public record BrowserPrincipalRow(String userId, String username, String email) { }

    public record GrantRow(String id, String userId, String clientId, String audience, String scopes, Instant absoluteExpiresAt,
                           Instant lastUsedAt, Instant revokedAt, String revocationReason, Instant createdAt) { }

    public record AuthorizationCodeRow(String id, String codeHash, String grantId, String clientId, String redirectUri,
                                       String codeChallenge, Instant expiresAt, Instant consumedAt, Instant createdAt) { }

    public record AccessTokenRow(String id, String tokenHash, String grantId, String userId, String clientId, String audience,
                                 String scopes, Instant issuedAt, Instant expiresAt, Instant revokedAt, String revocationReason) { }

    public record RefreshTokenRow(String id, String tokenHash, String grantId, String parentTokenId, String status,
                                  Instant issuedAt, Instant rotatedAt) { }

    public record AccessPrincipalRow(String tokenId, String grantId, String userId, String clientId, String audience,
                                     String scopes, Instant issuedAt, Instant expiresAt, String username, String email) { }
}

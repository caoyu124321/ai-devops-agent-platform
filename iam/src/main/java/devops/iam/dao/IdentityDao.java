package devops.iam.dao;

import devops.iam.persistence.mapper.IdentityMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 身份领域的数据访问门面，隔离服务层与 MyBatis 映射细节。 */
@Repository
public class IdentityDao {
    private final IdentityMapper mapper;

    public IdentityDao(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<UserRow> findUser(String login) { return Optional.ofNullable(mapper.findUser(login)); }

    public Optional<UserRow> findUserById(String userId) { return Optional.ofNullable(mapper.findUserById(userId)); }

    public void createUser(String id, String username, String email, String passwordHash, Instant now) {
        mapper.createUser(id, username, email, passwordHash, now);
    }

    public LockRow findLock(String userId) { return Optional.ofNullable(mapper.findLock(userId)).orElse(new LockRow(0, null)); }

    public void recordFailure(String userId, int count, Instant now, Instant lockedUntil) {
        if (mapper.updateFailure(userId, count, now, lockedUntil) == 0) { mapper.insertFailure(userId, count, now, lockedUntil); }
    }

    public void clearFailures(String userId) { mapper.clearFailures(userId); }

    public void createSession(String id, String userId, String tokenHash, Instant issuedAt, Instant expiresAt, String clientSummary) {
        mapper.createSession(id, userId, tokenHash, issuedAt, expiresAt, clientSummary);
    }

    public Optional<SessionRow> findActiveSession(String tokenHash, Instant now) { return Optional.ofNullable(mapper.findActiveSession(tokenHash, now)); }

    public void revokeSession(String id, Instant now, String reason) { mapper.revokeSession(id, now, reason); }

    public void revokeAll(String userId, Instant now) { mapper.revokeAll(userId, now); }

    public void updatePassword(String userId, String hash, Instant now) { mapper.updatePassword(userId, hash, now); }

    public record UserRow(String id, String username, String email, String passwordHash) { }
    public record LockRow(int failedCount, Instant lockedUntil) { }
    public record SessionRow(String id, String userId, Instant expiresAt) { }
}

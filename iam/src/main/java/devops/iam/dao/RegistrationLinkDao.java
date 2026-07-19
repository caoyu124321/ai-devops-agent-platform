package devops.iam.dao;

import devops.iam.identity.RegistrationLinkStatus;
import devops.iam.persistence.mapper.RegistrationLinkMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 隔离注册链接状态机与 MyBatis 查询细节，业务层不会接触令牌原文。 */
@Repository
public class RegistrationLinkDao {
    private final RegistrationLinkMapper mapper;

    public RegistrationLinkDao(RegistrationLinkMapper mapper) {
        this.mapper = mapper;
    }

    public void create(String id, String tokenHash, Instant createdAt, Instant expiresAt) {
        mapper.create(id, tokenHash, RegistrationLinkStatus.PENDING, createdAt, expiresAt);
    }

    public Optional<LinkRow> findById(String id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public Optional<LinkRow> findByIdForUpdate(String id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id));
    }

    public void expire(String id) {
        mapper.updateStatus(id, RegistrationLinkStatus.PENDING, RegistrationLinkStatus.EXPIRED);
    }

    public boolean complete(String id, Instant completedAt, String userId) {
        return mapper.complete(id, RegistrationLinkStatus.COMPLETED, completedAt, userId) == 1;
    }

    public record LinkRow(String id, String tokenHash, RegistrationLinkStatus status, Instant createdAt,
                          Instant expiresAt, Instant completedAt, String registeredUserId) {
    }
}

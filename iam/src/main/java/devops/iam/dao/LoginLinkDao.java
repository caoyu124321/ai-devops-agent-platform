package devops.iam.dao;

import devops.iam.identity.LoginLinkStatus;
import devops.iam.persistence.mapper.LoginLinkMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 登录链接的数据访问边界，防止业务层直接处理 MyBatis 映射。 */
@Repository
public class LoginLinkDao {
    private final LoginLinkMapper mapper;

    public LoginLinkDao(LoginLinkMapper mapper) {
        this.mapper = mapper;
    }

    public void create(String id, String tokenHash, String sessionTokenHash, Instant createdAt, Instant expiresAt) {
        mapper.create(id, tokenHash, sessionTokenHash, LoginLinkStatus.PENDING, createdAt, expiresAt);
    }

    public Optional<LinkRow> findById(String id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public Optional<LinkRow> findByIdForUpdate(String id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id));
    }

    public void expire(String id) {
        mapper.expire(id, LoginLinkStatus.EXPIRED);
    }

    public boolean complete(String id, Instant completedAt, Instant sessionExpiresAt, String userId) {
        return mapper.complete(id, LoginLinkStatus.COMPLETED, completedAt, sessionExpiresAt, userId) == 1;
    }

    public record LinkRow(String id, String tokenHash, String sessionTokenHash, LoginLinkStatus status, Instant createdAt,
                          Instant expiresAt, Instant completedAt, Instant sessionExpiresAt, String userId) {
    }
}

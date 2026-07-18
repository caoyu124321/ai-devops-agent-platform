package devops.iam.dao;

import devops.iam.persistence.mapper.AuthorizationGrantMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 内部授权项持久化门面；资源的业务归属不由 IAM 查询。 */
@Repository
public class AuthorizationGrantDao {
    private final AuthorizationGrantMapper mapper;

    public AuthorizationGrantDao(AuthorizationGrantMapper mapper) {
        this.mapper = mapper;
    }

    public List<GrantRow> listByTenant(String tenantId) {
        return mapper.listByTenant(tenantId);
    }

    public List<GrantRow> findMatches(String tenantId, String memberId, String resourceType, String resourceId,
                                      String actionCode, String environmentLevel) {
        return mapper.findMatches(tenantId, memberId, resourceType, resourceId, actionCode, environmentLevel);
    }

    public Optional<GrantRow> findById(String id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public void create(String id, String tenantId, String memberId, String resourceType, String resourceId,
                       String actionCode, String environmentLevel, String creatorId, Instant now) {
        mapper.create(id, tenantId, memberId, resourceType, resourceId, actionCode, environmentLevel, creatorId, now);
    }

    public void delete(String id) {
        mapper.delete(id);
    }

    public record GrantRow(String id, String tenantId, String memberId, String resourceType, String resourceId,
                           String actionCode, String environmentLevel, String effect, String createdBy,
                           Instant createdAt) { }
}

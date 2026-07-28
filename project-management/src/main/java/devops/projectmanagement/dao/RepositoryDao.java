package devops.projectmanagement.dao;

import devops.projectmanagement.persistence.mapper.RepositoryMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 仓库 DAO 负责当前配置、不可变版本和健康摘要的存取，不包含 GitHub 网络访问逻辑。 */
@Repository
public class RepositoryDao {
    private final RepositoryMapper mapper;

    public RepositoryDao(RepositoryMapper mapper) {
        this.mapper = mapper;
    }

    public void create(String id, String tenantId, String projectId, String canonicalUrl, String defaultBranch, String status,
                       Instant checkedAt, String errorCode, String createdBy, Instant now) {
        mapper.create(id, tenantId, projectId, canonicalUrl, defaultBranch, status, checkedAt, errorCode, createdBy, now);
    }

    public void createVersion(String id, String repositoryId, int versionNo, String canonicalUrl, String defaultBranch,
                              String createdBy, Instant now) {
        mapper.createVersion(id, repositoryId, versionNo, canonicalUrl, defaultBranch, createdBy, now);
    }

    public Optional<RepositoryRow> findById(String repositoryId) {
        return Optional.ofNullable(mapper.findById(repositoryId));
    }

    public List<RepositoryRow> listByProject(String projectId) {
        return mapper.listByProject(projectId);
    }

    public int countByProject(String projectId) {
        return mapper.countByProject(projectId);
    }

    public boolean update(String repositoryId, int expectedVersion, String canonicalUrl, String defaultBranch, String status,
                          Instant checkedAt, String errorCode, Instant now) {
        return mapper.update(repositoryId, expectedVersion, canonicalUrl, defaultBranch, status, checkedAt, errorCode, now) > 0;
    }

    public void updateHealth(String repositoryId, String status, Instant checkedAt, String errorCode, Instant now) {
        mapper.updateHealth(repositoryId, status, checkedAt, errorCode, now);
    }

    public boolean delete(String repositoryId) {
        return mapper.delete(repositoryId) > 0;
    }

    public record RepositoryRow(String id, String tenantId, String projectId, String canonicalUrl, String defaultBranch,
                                int currentVersionNo, String connectionStatus, Instant lastCheckedAt, String lastErrorCode,
                                String createdBy, Instant createdAt, Instant updatedAt) {
    }
}

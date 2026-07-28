package devops.projectmanagement.dao;

import devops.projectmanagement.persistence.mapper.ProjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 项目 DAO 只处理当前记录与不可变版本记录，不承载授权或业务校验。 */
@Repository
public class ProjectDao {
    private final ProjectMapper mapper;

    public ProjectDao(ProjectMapper mapper) {
        this.mapper = mapper;
    }

    public void create(ProjectRow row) {
        mapper.create(row.id(), row.tenantId(), row.name(), row.description(), row.currentVersionNo(), row.createdBy(),
                row.createdAt());
    }

    public void createVersion(String id, String projectId, int versionNo, String name, String description,
                              String createdBy, Instant createdAt) {
        mapper.createVersion(id, projectId, versionNo, name, description, createdBy, createdAt);
    }

    public Optional<ProjectRow> findById(String projectId) {
        return Optional.ofNullable(mapper.findById(projectId));
    }

    public List<ProjectRow> listByTenant(String tenantId) {
        return mapper.listByTenant(tenantId);
    }

    public boolean update(String projectId, int expectedVersion, String name, String description, Instant updatedAt) {
        return mapper.update(projectId, expectedVersion, name, description, updatedAt) > 0;
    }

    public boolean delete(String projectId) {
        return mapper.delete(projectId) > 0;
    }

    public record ProjectRow(String id, String tenantId, String name, String description, int currentVersionNo,
                             String createdBy, Instant createdAt, Instant updatedAt) {
    }
}

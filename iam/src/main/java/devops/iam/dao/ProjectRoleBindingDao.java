package devops.iam.dao;

import devops.iam.persistence.mapper.ProjectRoleBindingMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 项目角色绑定 DAO；项目 ID 是调用方提供的抽象标识，IAM 不访问项目模块。 */
@Repository
public class ProjectRoleBindingDao {
    private final ProjectRoleBindingMapper mapper;

    public ProjectRoleBindingDao(ProjectRoleBindingMapper mapper) {
        this.mapper = mapper;
    }

    public void create(String id, String tenantId, String memberId, String projectId, String roleCode,
                       String createdBy, Instant now) {
        mapper.create(id, tenantId, memberId, projectId, roleCode, createdBy, now);
    }

    public Optional<String> findRole(String tenantId, String memberId, String projectId) {
        return Optional.ofNullable(mapper.findRole(tenantId, memberId, projectId));
    }

    public List<ProjectRoleRow> listByMember(String tenantId, String memberId) {
        return mapper.listByMember(tenantId, memberId).stream()
                .map(row -> new ProjectRoleRow(row.projectId(), row.roleCode()))
                .toList();
    }

    public boolean deleteByMemberAndProject(String tenantId, String memberId, String projectId) {
        return mapper.deleteByMemberAndProject(tenantId, memberId, projectId) > 0;
    }

    public void deleteByMember(String memberId) {
        mapper.deleteByMember(memberId);
    }

    public void deleteByProject(String tenantId, String projectId) {
        mapper.deleteByProject(tenantId, projectId);
    }

    public record ProjectRoleRow(String projectId, String roleCode) { }
}

package devops.projectmanagement.dao;

import devops.projectmanagement.environment.EnvironmentTarget;
import devops.projectmanagement.domain.EnvironmentTargetType;
import devops.projectmanagement.persistence.mapper.EnvironmentMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 环境 DAO 处理版本化配置引用和非敏感目标快照，不包含连接校验与鉴权。 */
@Repository
public class EnvironmentDao {
    private final EnvironmentMapper mapper;

    public EnvironmentDao(EnvironmentMapper mapper) { this.mapper = mapper; }

    public void create(String id, String tenant, String project, String name, String type, String level, boolean enabled,
                       String status, Instant checked, String error, String actor, Instant now) {
        mapper.create(id, tenant, project, name, type, level, enabled, status, checked, error, actor, now);
    }

    public void version(String id, String environmentId, int version, String type, String level, String credential,
                        String actor, Instant now, EnvironmentTarget target) {
        mapper.version(id, environmentId, version, type, level, credential, actor, now);
        switch (target) {
            case EnvironmentTarget.KubernetesTarget value -> {
                mapper.createKubernetesConfig(id, value.apiServerUrl(), value.contextName(), value.defaultNamespace());
                value.allowedNamespaces().forEach(namespace -> mapper.namespace(UUID.randomUUID().toString(), id, namespace));
            }
            case EnvironmentTarget.LinuxHostTarget value -> mapper.linux(id, value.host(), value.port(), value.hostKeyFingerprint());
            case EnvironmentTarget.WindowsHostTarget value -> mapper.windows(id, value.endpointUrl(), value.certificateFingerprint());
        }
    }

    public Optional<EnvironmentRow> find(String id) { return Optional.ofNullable(mapper.find(id)); }
    public List<EnvironmentRow> list(String projectId) { return mapper.list(projectId); }
    public Optional<String> credential(String environmentId, int version) { return Optional.ofNullable(mapper.credential(environmentId, version)); }
    public Optional<EnvironmentTarget> target(String environmentId, int version, String type) {
        EnvironmentTargetType targetType = EnvironmentTargetType.valueOf(type);
        return switch (targetType) {
            case KUBERNETES -> Optional.ofNullable(mapper.kubernetes(environmentId, version)).map(row -> new EnvironmentTarget.KubernetesTarget(row.apiServerUrl(), row.contextName(), row.defaultNamespace(), mapper.namespaces(environmentId, version)));
            case LINUX_HOST -> Optional.ofNullable(mapper.linuxRead(environmentId, version)).map(row -> new EnvironmentTarget.LinuxHostTarget(row.host(), row.port(), row.fingerprint()));
            case WINDOWS_HOST -> Optional.ofNullable(mapper.windowsRead(environmentId, version)).map(row -> new EnvironmentTarget.WindowsHostTarget(row.endpointUrl(), row.fingerprint()));
        };
    }
    public boolean update(String id, int version, String name, String type, String level, boolean enabled, String status,
                          Instant checked, String error, Instant now) { return mapper.update(id, version, name, type, level, enabled, status, checked, error, now) > 0; }
    public boolean enabled(String id, int version, boolean enabled, Instant now) { return mapper.enabled(id, version, enabled, now) > 0; }
    public void health(String id, String status, Instant checked, String error, Instant now) { mapper.health(id, status, checked, error, now); }
    public boolean delete(String id) { return mapper.delete(id) > 0; }

    public record EnvironmentRow(String id, String tenantId, String projectId, String name, String targetType,
                                 String environmentLevel, boolean enabled, String connectionStatus, Instant lastCheckedAt,
                                 String lastErrorCode, int currentVersionNo, String createdBy, Instant createdAt, Instant updatedAt) { }
    public record KubernetesRow(String apiServerUrl, String contextName, String defaultNamespace) { }
    public record LinuxRow(String host, int port, String fingerprint) { }
    public record WindowsRow(String endpointUrl, String fingerprint) { }
}

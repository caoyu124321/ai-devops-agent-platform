package devops.projectmanagement.dao;

import devops.projectmanagement.persistence.mapper.CredentialMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 凭据 DAO 仅管理元数据、密文版本和项目授权，不返回解密后的秘密。 */
@Repository
public class CredentialDao {
    private final CredentialMapper mapper;

    public CredentialDao(CredentialMapper mapper) {
        this.mapper = mapper;
    }

    public void create(String id, String tenantId, String name, String type, String createdBy, Instant now) {
        mapper.create(id, tenantId, name, type, createdBy, now);
    }

    public void createVersion(String id, String credentialId, int versionNo, byte[] payload, String keyId, String algorithm,
                              String createdBy, Instant now) {
        mapper.createVersion(id, credentialId, versionNo, payload, keyId, algorithm, createdBy, now);
    }

    public Optional<CredentialRow> findById(String credentialId) {
        return Optional.ofNullable(mapper.findById(credentialId));
    }

    public Optional<CredentialVersionRow> findVersion(String credentialId, int versionNo) {
        return Optional.ofNullable(mapper.findVersion(credentialId, versionNo));
    }

    public List<CredentialRow> listByTenant(String tenantId) {
        return mapper.listByTenant(tenantId);
    }

    public List<CredentialRow> listGrantedByProject(String projectId) {
        return mapper.listGrantedByProject(projectId);
    }

    public boolean rename(String credentialId, int expectedVersion, String name, Instant now) {
        return mapper.rename(credentialId, expectedVersion, name, now) > 0;
    }

    public boolean rotate(String credentialId, int expectedVersion, Instant now) {
        return mapper.rotate(credentialId, expectedVersion, now) > 0;
    }

    public boolean disable(String credentialId, int expectedVersion, Instant now) {
        return mapper.disable(credentialId, expectedVersion, now) > 0;
    }

    public void grant(String id, String tenantId, String credentialId, String projectId, String grantedBy, Instant now) {
        mapper.grant(id, tenantId, credentialId, projectId, grantedBy, now);
    }

    public boolean revokeGrant(String credentialId, String projectId) {
        return mapper.revokeGrant(credentialId, projectId) > 0;
    }

    public boolean isGranted(String credentialId, String projectId) {
        return mapper.countGrant(credentialId, projectId) > 0;
    }

    public record CredentialRow(String id, String tenantId, String name, String credentialType, String status,
                                int currentVersionNo, String createdBy, Instant createdAt, Instant updatedAt) {
    }

    /** 密文版本仅供受控服务在轮换或元数据版本推进时复制，API 层不得直接读取。 */
    public record CredentialVersionRow(String id, String credentialId, int versionNo, byte[] encryptedPayload,
                                       String encryptionKeyId, String encryptionAlgorithm, String createdBy,
                                       Instant createdAt) {
    }
}

package devops.projectmanagement.service;

import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationScope;
import devops.iam.contract.AuthorizationService;
import devops.projectmanagement.api.ProjectManagementException;
import devops.projectmanagement.dao.CredentialDao;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.domain.CredentialStatus;
import devops.projectmanagement.domain.CredentialType;
import devops.projectmanagement.security.CredentialCryptoService;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 凭据服务将明文限制在单次写入请求的内存生命周期内：完成类型校验后立即加密，后续查询仅返回元数据。
 * 项目模块只向 IAM 提供资源、动作和范围，不读取或判断任何 IAM 角色名称。
 */
@Service
public class CredentialService {
    private static final String CREDENTIAL_RESOURCE = "CREDENTIAL";
    private static final String ENVIRONMENT_RESOURCE = "ENVIRONMENT";
    private static final String ACTION_MANAGE = "credential.manage";
    private static final String ACTION_VIEW = "credential.view";
    private static final String ACTION_GRANT = "credential.grant";
    private static final String ACTION_ENVIRONMENT_MODIFY = "environment.modify";
    private static final int MAX_NAME_LENGTH = 128;
    private final CredentialDao credentialDao;
    private final ProjectDao projectDao;
    private final AuthorizationService authorizationService;
    private final CredentialCryptoService cryptoService;

    public CredentialService(CredentialDao credentialDao, ProjectDao projectDao, AuthorizationService authorizationService,
                             CredentialCryptoService cryptoService) {
        this.credentialDao = credentialDao;
        this.projectDao = projectDao;
        this.authorizationService = authorizationService;
        this.cryptoService = cryptoService;
    }

    @Transactional
    public CredentialView create(String actorId, String tenantId, String name, String type, Map<String, String> secret) {
        CredentialType credentialType = parseType(type);
        validateName(name);
        require(actorId, tenantId, null, CREDENTIAL_RESOURCE, ACTION_MANAGE, AuthorizationScope.ScopeType.TENANT);
        CredentialCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(serializeSecret(credentialType, secret));
        String credentialId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try {
            credentialDao.create(credentialId, tenantId, name.trim(), credentialType.name(), actorId, now);
            credentialDao.createVersion(UUID.randomUUID().toString(), credentialId, 1, encrypted.payload(), encrypted.keyId(),
                    encrypted.algorithm(), actorId, now);
        } catch (DuplicateKeyException exception) {
            throw error("CREDENTIAL_NAME_EXISTS", HttpStatus.CONFLICT, "租户内凭据名称已存在");
        }
        return view(requireCredential(credentialId));
    }

    public List<CredentialView> list(String actorId, String tenantId) {
        require(actorId, tenantId, null, CREDENTIAL_RESOURCE, ACTION_VIEW, AuthorizationScope.ScopeType.TENANT);
        return credentialDao.listByTenant(tenantId).stream().map(this::view).toList();
    }

    public List<CredentialView> listReferencesForProject(String actorId, String projectId) {
        ProjectDao.ProjectRow project = requireProject(projectId);
        require(actorId, project.tenantId(), project.id(), ENVIRONMENT_RESOURCE, ACTION_ENVIRONMENT_MODIFY,
                AuthorizationScope.ScopeType.PROJECT);
        return credentialDao.listGrantedByProject(projectId).stream().map(this::view).toList();
    }

    @Transactional
    public CredentialView rename(String actorId, String credentialId, int expectedVersion, String name) {
        CredentialDao.CredentialRow current = requireCredential(credentialId);
        requireManage(actorId, current);
        validateName(name);
        requireActive(current);
        CredentialDao.CredentialVersionRow previousVersion = requireVersion(current);
        Instant now = Instant.now();
        try {
            if (!credentialDao.rename(credentialId, expectedVersion, name.trim(), now)) {
                throw error("CREDENTIAL_VERSION_CONFLICT", HttpStatus.CONFLICT, "凭据版本不匹配");
            }
        } catch (DuplicateKeyException exception) {
            throw error("CREDENTIAL_NAME_EXISTS", HttpStatus.CONFLICT, "租户内凭据名称已存在");
        }
        copyVersion(credentialId, expectedVersion + 1, previousVersion, actorId, now);
        return view(requireCredential(credentialId));
    }

    @Transactional
    public CredentialView rotate(String actorId, String credentialId, int expectedVersion, Map<String, String> secret) {
        CredentialDao.CredentialRow current = requireCredential(credentialId);
        requireManage(actorId, current);
        requireActive(current);
        CredentialType type = parseType(current.credentialType());
        CredentialCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(serializeSecret(type, secret));
        Instant now = Instant.now();
        if (!credentialDao.rotate(credentialId, expectedVersion, now)) {
            throw error("CREDENTIAL_VERSION_CONFLICT", HttpStatus.CONFLICT, "凭据版本不匹配");
        }
        credentialDao.createVersion(UUID.randomUUID().toString(), credentialId, expectedVersion + 1, encrypted.payload(),
                encrypted.keyId(), encrypted.algorithm(), actorId, now);
        return view(requireCredential(credentialId));
    }

    @Transactional
    public CredentialView disable(String actorId, String credentialId, int expectedVersion) {
        CredentialDao.CredentialRow current = requireCredential(credentialId);
        requireManage(actorId, current);
        requireActive(current);
        CredentialDao.CredentialVersionRow previousVersion = requireVersion(current);
        Instant now = Instant.now();
        if (!credentialDao.disable(credentialId, expectedVersion, now)) {
            throw error("CREDENTIAL_VERSION_CONFLICT", HttpStatus.CONFLICT, "凭据版本不匹配");
        }
        copyVersion(credentialId, expectedVersion + 1, previousVersion, actorId, now);
        return view(requireCredential(credentialId));
    }

    @Transactional
    public void grant(String actorId, String credentialId, String projectId) {
        CredentialDao.CredentialRow credential = requireCredential(credentialId);
        require(actorId, credential.tenantId(), null, CREDENTIAL_RESOURCE, ACTION_GRANT, AuthorizationScope.ScopeType.TENANT);
        requireActive(credential);
        ProjectDao.ProjectRow project = requireProject(projectId);
        if (!credential.tenantId().equals(project.tenantId())) {
            throw error("TENANT_MISMATCH", HttpStatus.BAD_REQUEST, "凭据与项目必须属于同一租户");
        }
        if (credentialDao.isGranted(credentialId, projectId)) {
            throw error("CREDENTIAL_ALREADY_GRANTED", HttpStatus.CONFLICT, "凭据已授权给该项目");
        }
        try {
            credentialDao.grant(UUID.randomUUID().toString(), credential.tenantId(), credentialId, projectId, actorId, Instant.now());
        } catch (DuplicateKeyException exception) {
            throw error("CREDENTIAL_ALREADY_GRANTED", HttpStatus.CONFLICT, "凭据已授权给该项目");
        }
    }

    @Transactional
    public void revokeGrant(String actorId, String credentialId, String projectId) {
        CredentialDao.CredentialRow credential = requireCredential(credentialId);
        require(actorId, credential.tenantId(), null, CREDENTIAL_RESOURCE, ACTION_GRANT, AuthorizationScope.ScopeType.TENANT);
        if (!credentialDao.revokeGrant(credentialId, projectId)) {
            throw error("CREDENTIAL_GRANT_NOT_FOUND", HttpStatus.NOT_FOUND, "凭据项目授权不存在");
        }
    }

    private void copyVersion(String credentialId, int versionNo, CredentialDao.CredentialVersionRow source,
                             String actorId, Instant now) {
        credentialDao.createVersion(UUID.randomUUID().toString(), credentialId, versionNo, source.encryptedPayload(),
                source.encryptionKeyId(), source.encryptionAlgorithm(), actorId, now);
    }

    private CredentialDao.CredentialRow requireCredential(String credentialId) {
        return credentialDao.findById(credentialId)
                .orElseThrow(() -> error("CREDENTIAL_NOT_FOUND", HttpStatus.NOT_FOUND, "凭据不存在或不可见"));
    }

    private CredentialDao.CredentialVersionRow requireVersion(CredentialDao.CredentialRow credential) {
        return credentialDao.findVersion(credential.id(), credential.currentVersionNo())
                .orElseThrow(() -> error("CREDENTIAL_VERSION_NOT_FOUND", HttpStatus.INTERNAL_SERVER_ERROR, "凭据版本数据不完整"));
    }

    private ProjectDao.ProjectRow requireProject(String projectId) {
        return projectDao.findById(projectId)
                .orElseThrow(() -> error("PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "项目不存在或不可见"));
    }

    private void requireManage(String actorId, CredentialDao.CredentialRow credential) {
        require(actorId, credential.tenantId(), null, CREDENTIAL_RESOURCE, ACTION_MANAGE, AuthorizationScope.ScopeType.TENANT);
    }

    private void require(String actorId, String tenantId, String projectId, String resource, String action,
                         AuthorizationScope.ScopeType scopeType) {
        authorizationService.requireAuthorization(new AuthorizationRequest(new AuthenticatedSubject(actorId, null, Instant.now()),
                resource, resource, action, new AuthorizationScope(scopeType, tenantId, projectId, null, null), Map.of()));
    }

    private CredentialType parseType(String type) {
        try {
            return CredentialType.valueOf(type == null ? "" : type.trim());
        } catch (IllegalArgumentException exception) {
            throw error("CREDENTIAL_TYPE_INVALID", HttpStatus.BAD_REQUEST, "不支持的凭据类型");
        }
    }

    private String serializeSecret(CredentialType type, Map<String, String> secret) {
        if (secret == null) {
            throw error("CREDENTIAL_PAYLOAD_INVALID", HttpStatus.BAD_REQUEST, "凭据内容不能为空");
        }
        Set<String> allowed = requiredSecretFields(type);
        if (!secret.keySet().equals(allowed) && !(type == CredentialType.SSH_PRIVATE_KEY
                && secret.keySet().equals(Set.of("username", "privateKey", "passphrase")))) {
            throw error("CREDENTIAL_PAYLOAD_INVALID", HttpStatus.BAD_REQUEST, "凭据内容字段不符合类型要求");
        }
        for (String field : allowed) {
            if (secret.get(field) == null || secret.get(field).isBlank()) {
                throw error("CREDENTIAL_PAYLOAD_INVALID", HttpStatus.BAD_REQUEST, "凭据内容不能为空");
            }
        }
        return secret.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> "\"" + escapeJson(entry.getKey()) + "\":\"" + escapeJson(entry.getValue()) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private Set<String> requiredSecretFields(CredentialType type) {
        return switch (type) {
            case KUBECONFIG -> Set.of("kubeconfig");
            case SSH_PASSWORD, WINRM_PASSWORD -> Set.of("username", "password");
            case SSH_PRIVATE_KEY -> new LinkedHashSet<>(List.of("username", "privateKey"));
            case GITHUB_TOKEN -> Set.of("token");
        };
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > MAX_NAME_LENGTH) {
            throw error("CREDENTIAL_NAME_INVALID", HttpStatus.BAD_REQUEST, "凭据名称必须为 1 到 128 个字符");
        }
    }

    private void requireActive(CredentialDao.CredentialRow credential) {
        if (CredentialStatus.DISABLED.name().equals(credential.status())) {
            throw error("CREDENTIAL_DISABLED", HttpStatus.CONFLICT, "凭据已停用");
        }
    }

    private CredentialView view(CredentialDao.CredentialRow row) {
        return new CredentialView(row.id(), row.tenantId(), row.name(), row.credentialType(), row.status(),
                row.currentVersionNo(), row.createdAt(), row.updatedAt());
    }

    private ProjectManagementException error(String code, HttpStatus status, String message) {
        return new ProjectManagementException(code, status, message);
    }

    /** 对外视图刻意不包含密文、密钥标识或任何明文秘密字段。 */
    public record CredentialView(String id, String tenantId, String name, String type, String status, int version,
                                 Instant createdAt, Instant updatedAt) {
    }
}

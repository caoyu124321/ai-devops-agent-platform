package devops.projectmanagement.service;

import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationScope;
import devops.iam.contract.AuthorizationService;
import devops.projectmanagement.api.ProjectManagementException;
import devops.projectmanagement.dao.CredentialDao;
import devops.projectmanagement.dao.EnvironmentDao;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.domain.ConnectionStatus;
import devops.projectmanagement.domain.CredentialStatus;
import devops.projectmanagement.domain.CredentialType;
import devops.projectmanagement.domain.EnvironmentLevel;
import devops.projectmanagement.environment.EnvironmentConnectionValidator;
import devops.projectmanagement.environment.EnvironmentTarget;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 环境服务统一执行项目范围授权、凭据引用校验、目标配置校验与只读连接健康检查。 */
@Service
public class EnvironmentService {
    private static final String RESOURCE = "ENVIRONMENT";
    private static final String MODIFY = "environment.modify";
    private static final String VIEW = "environment.view";
    private final EnvironmentDao environmentDao;
    private final ProjectDao projectDao;
    private final CredentialDao credentialDao;
    private final AuthorizationService authorizationService;
    private final EnvironmentConnectionValidator validator;

    public EnvironmentService(EnvironmentDao environmentDao, ProjectDao projectDao, CredentialDao credentialDao,
                              AuthorizationService authorizationService, EnvironmentConnectionValidator validator) {
        this.environmentDao = environmentDao; this.projectDao = projectDao; this.credentialDao = credentialDao;
        this.authorizationService = authorizationService; this.validator = validator;
    }

    @Transactional
    public EnvironmentView create(String actorId, String projectId, String name, String level, String credentialId, EnvironmentTarget target) {
        ProjectDao.ProjectRow project = project(projectId); require(actorId, project, MODIFY); validate(name, level, target);
        validateCredential(project, credentialId, target); EnvironmentConnectionValidator.ValidationResult result = validator.validate(target, credentialId);
        String id = UUID.randomUUID().toString(); Instant now = Instant.now();
        try { environmentDao.create(id, project.tenantId(), projectId, name.trim(), target.type().name(), level, false, result.status().name(), now, result.errorCode(), actorId, now);
            environmentDao.version(UUID.randomUUID().toString(), id, 1, target.type().name(), level, credentialId, actorId, now, target);
        } catch (DuplicateKeyException exception) { throw error("ENVIRONMENT_NAME_EXISTS", HttpStatus.CONFLICT, "项目内环境名称已存在"); }
        return view(require(id));
    }

    public List<EnvironmentView> list(String actorId, String projectId) { ProjectDao.ProjectRow project = project(projectId); require(actorId, project, VIEW); return environmentDao.list(projectId).stream().map(this::view).toList(); }
    public EnvironmentView get(String actorId, String id) { EnvironmentDao.EnvironmentRow row = require(id); require(actorId, project(row.projectId()), VIEW); return view(row); }

    @Transactional
    public EnvironmentView update(String actorId, String id, int version, String name, String level, String credentialId, EnvironmentTarget target) {
        EnvironmentDao.EnvironmentRow current = require(id); ProjectDao.ProjectRow project = project(current.projectId()); require(actorId, project, MODIFY); validate(name, level, target); validateCredential(project, credentialId, target);
        EnvironmentConnectionValidator.ValidationResult result = validator.validate(target, credentialId); Instant now = Instant.now();
        try { if (!environmentDao.update(id, version, name.trim(), target.type().name(), level, current.enabled(), result.status().name(), now, result.errorCode(), now)) throw error("ENVIRONMENT_VERSION_CONFLICT", HttpStatus.CONFLICT, "环境版本不匹配"); }
        catch (DuplicateKeyException exception) { throw error("ENVIRONMENT_NAME_EXISTS", HttpStatus.CONFLICT, "项目内环境名称已存在"); }
        environmentDao.version(UUID.randomUUID().toString(), id, version + 1, target.type().name(), level, credentialId, actorId, now, target); return view(require(id));
    }

    @Transactional
    public EnvironmentView setEnabled(String actorId, String id, int version, boolean enabled) {
        EnvironmentDao.EnvironmentRow row = require(id); require(actorId, project(row.projectId()), MODIFY);
        if (!environmentDao.enabled(id, version, enabled, Instant.now())) throw error("ENVIRONMENT_VERSION_CONFLICT", HttpStatus.CONFLICT, "环境版本不匹配"); return view(require(id));
    }
    @Transactional
    public EnvironmentView validate(String actorId, String id) {
        EnvironmentDao.EnvironmentRow row = require(id); require(actorId, project(row.projectId()), MODIFY);
        String credentialId = environmentDao.credential(id, row.currentVersionNo()).orElseThrow(() -> error("ENVIRONMENT_CONFIGURATION_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, "环境版本数据不完整"));
        EnvironmentTarget target = environmentDao.target(id, row.currentVersionNo(), row.targetType())
                .orElseThrow(() -> error("ENVIRONMENT_CONFIGURATION_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, "环境目标配置不完整"));
        EnvironmentConnectionValidator.ValidationResult result = validator.validate(target, credentialId);
        Instant now = Instant.now();
        environmentDao.health(id, result.status().name(), now, result.errorCode(), now); return view(require(id));
    }
    @Transactional public void delete(String actorId, String id) { EnvironmentDao.EnvironmentRow row = require(id); require(actorId, project(row.projectId()), MODIFY); environmentDao.delete(id); }

    private void validateCredential(ProjectDao.ProjectRow project, String credentialId, EnvironmentTarget target) {
        CredentialDao.CredentialRow credential = credentialDao.findById(credentialId).orElseThrow(() -> error("CREDENTIAL_NOT_FOUND", HttpStatus.NOT_FOUND, "凭据不存在或不可见"));
        if (!project.tenantId().equals(credential.tenantId())) throw error("TENANT_MISMATCH", HttpStatus.BAD_REQUEST, "凭据与项目必须属于同一租户");
        if (!CredentialStatus.ACTIVE.name().equals(credential.status())) throw error("CREDENTIAL_DISABLED", HttpStatus.CONFLICT, "凭据已停用");
        if (!credentialDao.isGranted(credentialId, project.id())) throw error("CREDENTIAL_NOT_GRANTED", HttpStatus.BAD_REQUEST, "凭据未授权给项目");
        boolean allowed = switch (target) { case EnvironmentTarget.KubernetesTarget ignored -> CredentialType.KUBECONFIG.name().equals(credential.credentialType()); case EnvironmentTarget.LinuxHostTarget ignored -> CredentialType.SSH_PASSWORD.name().equals(credential.credentialType()) || CredentialType.SSH_PRIVATE_KEY.name().equals(credential.credentialType()); case EnvironmentTarget.WindowsHostTarget ignored -> CredentialType.WINRM_PASSWORD.name().equals(credential.credentialType()); };
        if (!allowed) throw error("CREDENTIAL_TYPE_MISMATCH", HttpStatus.BAD_REQUEST, "凭据类型与环境目标不匹配");
    }
    private void validate(String name, String level, EnvironmentTarget target) { if (name == null || name.isBlank() || name.trim().length() > 128) throw error("ENVIRONMENT_NAME_INVALID", HttpStatus.BAD_REQUEST, "环境名称必须为 1 到 128 个字符"); try { EnvironmentLevel.valueOf(level); } catch (Exception exception) { throw error("ENVIRONMENT_LEVEL_INVALID", HttpStatus.BAD_REQUEST, "环境等级不受支持"); } if (target == null) throw error("TARGET_CONFIGURATION_INVALID", HttpStatus.BAD_REQUEST, "环境目标不能为空"); switch(target) { case EnvironmentTarget.KubernetesTarget value -> { if (!https(value.apiServerUrl()) || value.defaultNamespace()==null || value.defaultNamespace().isBlank() || value.allowedNamespaces()==null || !value.allowedNamespaces().contains(value.defaultNamespace())) throw error("TARGET_CONFIGURATION_INVALID", HttpStatus.BAD_REQUEST, "Kubernetes 命名空间或 API 地址无效"); } case EnvironmentTarget.LinuxHostTarget value -> { if (value.host()==null || value.host().isBlank() || value.port()<1 || value.port()>65535 || blank(value.hostKeyFingerprint())) throw error("TARGET_CONFIGURATION_INVALID", HttpStatus.BAD_REQUEST, "SSH 主机配置无效"); } case EnvironmentTarget.WindowsHostTarget value -> { if (!https(value.endpointUrl()) || blank(value.certificateFingerprint())) throw error("TARGET_CONFIGURATION_INVALID", HttpStatus.BAD_REQUEST, "WinRM HTTPS 配置无效"); }} }
    private boolean https(String value) { try { return value != null && "https".equalsIgnoreCase(URI.create(value).getScheme()); } catch (Exception exception) { return false; } } private boolean blank(String value){return value==null||value.isBlank();}
    private EnvironmentDao.EnvironmentRow require(String id){return environmentDao.find(id).orElseThrow(()->error("ENVIRONMENT_NOT_FOUND",HttpStatus.NOT_FOUND,"环境不存在或不可见"));} private ProjectDao.ProjectRow project(String id){return projectDao.findById(id).orElseThrow(()->error("PROJECT_NOT_FOUND",HttpStatus.NOT_FOUND,"项目不存在或不可见"));}
    private void require(String actor,ProjectDao.ProjectRow project,String action){authorizationService.requireAuthorization(new AuthorizationRequest(new AuthenticatedSubject(actor,null,Instant.now()),RESOURCE,project.id(),action,new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT,project.tenantId(),project.id(),null,null),Map.of()));}
    private EnvironmentView view(EnvironmentDao.EnvironmentRow row){return new EnvironmentView(row.id(),row.tenantId(),row.projectId(),row.name(),row.targetType(),row.environmentLevel(),row.enabled(),row.connectionStatus(),row.lastCheckedAt(),row.lastErrorCode(),row.currentVersionNo(),row.createdAt(),row.updatedAt());} private ProjectManagementException error(String c,HttpStatus s,String m){return new ProjectManagementException(c,s,m);}
    public record EnvironmentView(String id,String tenantId,String projectId,String name,String targetType,String level,boolean enabled,String connectionStatus,Instant lastCheckedAt,String lastErrorCode,int version,Instant createdAt,Instant updatedAt){}
}

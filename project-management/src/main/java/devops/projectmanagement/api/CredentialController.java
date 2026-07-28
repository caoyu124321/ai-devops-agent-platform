package devops.projectmanagement.api;

import devops.projectmanagement.service.CredentialService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 凭据 Controller 仅传递输入；任何秘密字段只进入写入服务，响应类型始终为不含秘密的 CredentialView。 */
@RestController
@RequestMapping("/api/v1")
public class CredentialController {
    private final CredentialService credentialService;
    private final CurrentActorResolver actorResolver;

    public CredentialController(CredentialService credentialService, CurrentActorResolver actorResolver) {
        this.credentialService = credentialService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/tenants/{tenantId}/credentials")
    public ResponseEntity<CredentialService.CredentialView> create(HttpServletRequest request, @PathVariable String tenantId,
                                                                     @RequestBody CreateCredentialRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(credentialService.create(actorResolver.requireActorId(request), tenantId,
                body.name(), body.type(), body.secret()));
    }

    @GetMapping("/tenants/{tenantId}/credentials")
    public List<CredentialService.CredentialView> list(HttpServletRequest request, @PathVariable String tenantId) {
        return credentialService.list(actorResolver.requireActorId(request), tenantId);
    }

    @GetMapping("/projects/{projectId}/credential-references")
    public List<CredentialService.CredentialView> references(HttpServletRequest request, @PathVariable String projectId) {
        return credentialService.listReferencesForProject(actorResolver.requireActorId(request), projectId);
    }

    @PatchMapping("/credentials/{credentialId}")
    public CredentialService.CredentialView rename(HttpServletRequest request, @PathVariable String credentialId,
                                                    @RequestBody RenameCredentialRequest body) {
        return credentialService.rename(actorResolver.requireActorId(request), credentialId, body.expectedVersion(), body.name());
    }

    @PostMapping("/credentials/{credentialId}/rotations")
    public ResponseEntity<CredentialService.CredentialView> rotate(HttpServletRequest request, @PathVariable String credentialId,
                                                                     @RequestBody RotateCredentialRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(credentialService.rotate(actorResolver.requireActorId(request), credentialId,
                body.expectedVersion(), body.secret()));
    }

    @PostMapping("/credentials/{credentialId}/disable")
    public CredentialService.CredentialView disable(HttpServletRequest request, @PathVariable String credentialId,
                                                     @RequestBody VersionRequest body) {
        return credentialService.disable(actorResolver.requireActorId(request), credentialId, body.expectedVersion());
    }

    @PostMapping("/credentials/{credentialId}/project-grants")
    public ResponseEntity<Void> grant(HttpServletRequest request, @PathVariable String credentialId, @RequestBody ProjectGrantRequest body) {
        credentialService.grant(actorResolver.requireActorId(request), credentialId, body.projectId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/credentials/{credentialId}/project-grants/{projectId}")
    public ResponseEntity<Void> revoke(HttpServletRequest request, @PathVariable String credentialId, @PathVariable String projectId) {
        credentialService.revokeGrant(actorResolver.requireActorId(request), credentialId, projectId);
        return ResponseEntity.noContent().build();
    }

    public record CreateCredentialRequest(String name, String type, Map<String, String> secret) { }
    public record RenameCredentialRequest(String name, int expectedVersion) { }
    public record RotateCredentialRequest(int expectedVersion, Map<String, String> secret) { }
    public record VersionRequest(int expectedVersion) { }
    public record ProjectGrantRequest(String projectId) { }
}

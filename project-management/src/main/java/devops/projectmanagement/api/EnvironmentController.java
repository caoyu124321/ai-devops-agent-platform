package devops.projectmanagement.api;

import devops.projectmanagement.service.EnvironmentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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

/** 环境 Controller 只转换 HTTP 请求；鉴权、配置及连接规则全部在服务层完成。 */
@RestController
@RequestMapping("/api/v1")
public class EnvironmentController {
    private final EnvironmentService service;
    private final CurrentActorResolver actorResolver;
    private final EnvironmentRequestMapper requestMapper;

    public EnvironmentController(EnvironmentService service, CurrentActorResolver actorResolver,
                                 EnvironmentRequestMapper requestMapper) {
        this.service = service;
        this.actorResolver = actorResolver;
        this.requestMapper = requestMapper;
    }

    @PostMapping("/projects/{projectId}/environments")
    public ResponseEntity<EnvironmentService.EnvironmentView> create(HttpServletRequest request, @PathVariable String projectId,
                                                                      @RequestBody EnvironmentRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actorResolver.requireActorId(request), projectId,
                body.name(), body.level(), body.credentialId(), requestMapper.target(body)));
    }

    @GetMapping("/projects/{projectId}/environments")
    public List<EnvironmentService.EnvironmentView> list(HttpServletRequest request, @PathVariable String projectId) {
        return service.list(actorResolver.requireActorId(request), projectId);
    }

    @GetMapping("/environments/{id}")
    public EnvironmentService.EnvironmentView get(HttpServletRequest request, @PathVariable String id) {
        return service.get(actorResolver.requireActorId(request), id);
    }

    @PatchMapping("/environments/{id}")
    public EnvironmentService.EnvironmentView update(HttpServletRequest request, @PathVariable String id,
                                                      @RequestBody UpdateEnvironmentRequest body) {
        return service.update(actorResolver.requireActorId(request), id, body.expectedVersion(), body.name(), body.level(),
                body.credentialId(), requestMapper.target(body));
    }

    @PostMapping("/environments/{id}/enable")
    public EnvironmentService.EnvironmentView enable(HttpServletRequest request, @PathVariable String id,
                                                      @RequestBody VersionRequest body) {
        return service.setEnabled(actorResolver.requireActorId(request), id, body.expectedVersion(), true);
    }

    @PostMapping("/environments/{id}/disable")
    public EnvironmentService.EnvironmentView disable(HttpServletRequest request, @PathVariable String id,
                                                       @RequestBody VersionRequest body) {
        return service.setEnabled(actorResolver.requireActorId(request), id, body.expectedVersion(), false);
    }

    @PostMapping("/environments/{id}/validation")
    public EnvironmentService.EnvironmentView validate(HttpServletRequest request, @PathVariable String id) {
        return service.validate(actorResolver.requireActorId(request), id);
    }

    @DeleteMapping("/environments/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String id) {
        service.delete(actorResolver.requireActorId(request), id);
        return ResponseEntity.noContent().build();
    }

    public record EnvironmentRequest(String name, String level, String credentialId, String targetType,
                                     String apiServerUrl, String contextName, String defaultNamespace,
                                     List<String> allowedNamespaces, String host, int port, String hostKeyFingerprint,
                                     String endpointUrl, String certificateFingerprint) { }

    public record UpdateEnvironmentRequest(String name, String level, String credentialId, String targetType,
                                           String apiServerUrl, String contextName, String defaultNamespace,
                                           List<String> allowedNamespaces, String host, int port, String hostKeyFingerprint,
                                           String endpointUrl, String certificateFingerprint, int expectedVersion) { }

    public record VersionRequest(int expectedVersion) { }
}

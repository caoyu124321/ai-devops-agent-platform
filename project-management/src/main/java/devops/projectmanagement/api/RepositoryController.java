package devops.projectmanagement.api;

import devops.projectmanagement.service.RepositoryService;
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

/** 仓库 Controller 不访问 GitHub；校验、版本控制和删除决策均由服务层执行。 */
@RestController
@RequestMapping("/api/v1")
public class RepositoryController {
    private final RepositoryService repositoryService;
    private final CurrentActorResolver actorResolver;

    public RepositoryController(RepositoryService repositoryService, CurrentActorResolver actorResolver) {
        this.repositoryService = repositoryService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/projects/{projectId}/repositories")
    public ResponseEntity<RepositoryService.RepositoryView> create(HttpServletRequest request, @PathVariable String projectId,
                                                                     @RequestBody RepositoryRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repositoryService.create(actorResolver.requireActorId(request), projectId,
                body.url(), body.defaultBranch()));
    }

    @GetMapping("/projects/{projectId}/repositories")
    public List<RepositoryService.RepositoryView> list(HttpServletRequest request, @PathVariable String projectId) {
        return repositoryService.list(actorResolver.requireActorId(request), projectId);
    }

    @PatchMapping("/repositories/{repositoryId}")
    public RepositoryService.RepositoryView update(HttpServletRequest request, @PathVariable String repositoryId,
                                                    @RequestBody UpdateRepositoryRequest body) {
        return repositoryService.update(actorResolver.requireActorId(request), repositoryId, body.expectedVersion(), body.url(), body.defaultBranch());
    }

    @PostMapping("/repositories/{repositoryId}/validation")
    public RepositoryService.RepositoryView validate(HttpServletRequest request, @PathVariable String repositoryId) {
        return repositoryService.validate(actorResolver.requireActorId(request), repositoryId);
    }

    @DeleteMapping("/repositories/{repositoryId}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String repositoryId) {
        repositoryService.delete(actorResolver.requireActorId(request), repositoryId);
        return ResponseEntity.noContent().build();
    }

    public record RepositoryRequest(String url, String defaultBranch) { }
    public record UpdateRepositoryRequest(String url, String defaultBranch, int expectedVersion) { }
}

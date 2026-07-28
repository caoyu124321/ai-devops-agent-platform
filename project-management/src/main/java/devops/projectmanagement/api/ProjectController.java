package devops.projectmanagement.api;

import devops.projectmanagement.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 项目接口只完成请求与响应转换，授权、事务、版本与成员绑定规则都由服务层处理。 */
@RestController
@RequestMapping("/api/v1")
public class ProjectController {
    private final ProjectService projectService;
    private final CurrentActorResolver actorResolver;

    public ProjectController(ProjectService projectService, CurrentActorResolver actorResolver) {
        this.projectService = projectService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/tenants/{tenantId}/projects")
    public ResponseEntity<ProjectService.ProjectView> create(HttpServletRequest request, @PathVariable String tenantId,
                                                              @RequestBody CreateProjectRequest body) {
        ProjectService.ProjectView project = projectService.create(actorResolver.requireActorId(request), tenantId, body.name(), body.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping("/tenants/{tenantId}/projects")
    public List<ProjectService.ProjectView> list(HttpServletRequest request, @PathVariable String tenantId) {
        return projectService.list(actorResolver.requireActorId(request), tenantId);
    }

    @GetMapping("/projects/{projectId}")
    public ProjectService.ProjectView get(HttpServletRequest request, @PathVariable String projectId) {
        return projectService.get(actorResolver.requireActorId(request), projectId);
    }

    @PatchMapping("/projects/{projectId}")
    public ProjectService.ProjectView update(HttpServletRequest request, @PathVariable String projectId,
                                             @RequestBody UpdateProjectRequest body) {
        return projectService.update(actorResolver.requireActorId(request), projectId, body.expectedVersion(), body.name(), body.description());
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String projectId) {
        projectService.delete(actorResolver.requireActorId(request), projectId);
        return ResponseEntity.noContent().build();
    }

    public record CreateProjectRequest(String name, String description) {
    }

    public record UpdateProjectRequest(String name, String description, int expectedVersion) {
    }
}

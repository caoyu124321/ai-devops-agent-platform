package devops.pipeline.api;

import devops.pipeline.service.PipelineService;
import devops.pipeline.service.PipelineRunService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller 只负责 HTTP 转换，YAML 校验、授权、版本和插件检查均由 PipelineService 完成。 */
@RestController
@RequestMapping("/api/v1")
public class PipelineController {
    private final PipelineService pipelineService;
    private final PipelineRunService pipelineRunService;
    private final PipelineCurrentActorResolver actorResolver;

    public PipelineController(PipelineService pipelineService, PipelineRunService pipelineRunService,
                              PipelineCurrentActorResolver actorResolver) {
        this.pipelineService = pipelineService;
        this.pipelineRunService = pipelineRunService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/projects/{projectId}/pipelines/validate")
    public PipelineService.ValidationView validate(HttpServletRequest request, @PathVariable String projectId,
                                                   @RequestBody ValidatePipelineRequest body) {
        return pipelineService.validate(actorResolver.requireActorId(request), projectId, body.yamlContent());
    }

    @PostMapping("/projects/{projectId}/pipelines")
    public ResponseEntity<PipelineService.PipelineView> create(HttpServletRequest request, @PathVariable String projectId,
                                                                @RequestBody CreatePipelineRequest body) {
        PipelineService.PipelineView view = pipelineService.create(actorResolver.requireActorId(request), projectId,
                body.name(), body.description(), body.yamlContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/projects/{projectId}/pipelines")
    public List<PipelineService.PipelineView> list(HttpServletRequest request, @PathVariable String projectId) {
        return pipelineService.list(actorResolver.requireActorId(request), projectId);
    }

    @GetMapping("/pipelines/{pipelineId}")
    public PipelineService.PipelineView get(HttpServletRequest request, @PathVariable String pipelineId) {
        return pipelineService.get(actorResolver.requireActorId(request), pipelineId);
    }

    @PutMapping("/pipelines/{pipelineId}")
    public PipelineService.PipelineView update(HttpServletRequest request, @PathVariable String pipelineId,
                                               @RequestBody UpdatePipelineRequest body) {
        return pipelineService.update(actorResolver.requireActorId(request), pipelineId, body.expectedVersion(), body.name(),
                body.description(), body.yamlContent());
    }

    @PostMapping("/pipelines/{pipelineId}/enabled")
    public PipelineService.PipelineView setEnabled(HttpServletRequest request, @PathVariable String pipelineId,
                                                   @RequestBody SetPipelineEnabledRequest body) {
        return pipelineService.setEnabled(actorResolver.requireActorId(request), pipelineId, body.expectedVersion(), body.enabled());
    }

    @GetMapping("/pipelines/{pipelineId}/versions")
    public List<PipelineService.PipelineVersionView> listVersions(HttpServletRequest request, @PathVariable String pipelineId) {
        return pipelineService.listVersions(actorResolver.requireActorId(request), pipelineId);
    }

    @PostMapping("/pipeline-versions/{versionId}/runs")
    public ResponseEntity<PipelineRunService.RunView> createRun(HttpServletRequest request, @PathVariable String versionId,
                                                                 @RequestBody CreateRunRequest body) {
        PipelineRunService.RunView view = pipelineRunService.create(actorResolver.requireActorId(request), versionId,
                body.branch(), body.commit(), body.parameters(), request.getHeader("Idempotency-Key"));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/runs/{runId}")
    public PipelineRunService.RunDetailView getRun(HttpServletRequest request, @PathVariable String runId) {
        return pipelineRunService.get(actorResolver.requireActorId(request), runId);
    }

    @PostMapping("/runs/{runId}/cancel")
    public PipelineRunService.RunView cancelRun(HttpServletRequest request, @PathVariable String runId) {
        return pipelineRunService.cancel(actorResolver.requireActorId(request), runId);
    }

    @GetMapping("/runs/{runId}/logs")
    public List<PipelineRunService.LogView> logs(HttpServletRequest request, @PathVariable String runId) {
        return pipelineRunService.logs(actorResolver.requireActorId(request), runId);
    }

    @PostMapping("/runs/{runId}/retry")
    public ResponseEntity<PipelineRunService.RunView> retryRun(HttpServletRequest request, @PathVariable String runId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pipelineRunService.retry(actorResolver.requireActorId(request), runId));
    }

    public record ValidatePipelineRequest(String yamlContent) {
    }

    public record CreatePipelineRequest(String name, String description, String yamlContent) {
    }

    public record UpdatePipelineRequest(int expectedVersion, String name, String description, String yamlContent) {
    }

    public record SetPipelineEnabledRequest(int expectedVersion, boolean enabled) {
    }

    public record CreateRunRequest(String branch, String commit, Map<String, Object> parameters) {
    }
}

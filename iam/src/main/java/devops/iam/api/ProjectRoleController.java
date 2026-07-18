package devops.iam.api;

import devops.iam.authorization.ProjectRoleService;
import devops.iam.identity.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员为租户成员分配固定项目角色，底层授权项不会向用户暴露。 */
@RestController
@RequestMapping("/api/v1")
class ProjectRoleController {
    private final ProjectRoleService service;

    ProjectRoleController(ProjectRoleService service) {
        this.service = service;
    }

    @PostMapping("/tenants/{tenantId}/members/{memberId}/project-roles")
    Map<String, Object> bind(@PathVariable String tenantId, @PathVariable String memberId,
                             @RequestBody BindRequest body, HttpServletRequest request) {
        service.bind(userId(request), tenantId, memberId, body.projectId(), body.roleCode());
        return Map.of();
    }

    @GetMapping("/tenants/{tenantId}/members/{memberId}/project-roles")
    List<ProjectRoleService.ProjectRoleView> list(@PathVariable String tenantId, @PathVariable String memberId,
                                                  HttpServletRequest request) {
        return service.list(userId(request), tenantId, memberId);
    }

    @DeleteMapping("/tenants/{tenantId}/members/{memberId}/project-roles/{projectId}")
    Map<String, Object> unbind(@PathVariable String tenantId, @PathVariable String memberId,
                               @PathVariable String projectId, HttpServletRequest request) {
        service.unbind(userId(request), tenantId, memberId, projectId);
        return Map.of();
    }

    private String userId(HttpServletRequest request) {
        Object value = request.getAttribute("iamPrincipal");
        if (value instanceof IdentityService.SessionPrincipal principal) {
            return principal.user().id();
        }
        throw new IamException("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "需要登录");
    }

    record BindRequest(String projectId, String roleCode) { }
}

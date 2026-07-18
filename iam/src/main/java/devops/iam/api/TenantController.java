package devops.iam.api;

import devops.iam.identity.IdentityService;
import devops.iam.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 租户和成员 REST 协议层，不在控制器中放置业务规则。 */
@RestController
@RequestMapping("/api/v1")
class TenantController {
    private final TenantService service;

    TenantController(TenantService service) {
        this.service = service;
    }

    @PostMapping("/tenants")
    TenantService.TenantView create(@RequestBody CreateTenantRequest body, HttpServletRequest request) {
        return service.create(userId(request), body.name());
    }

    @GetMapping("/tenants")
    List<TenantService.TenantView> list(HttpServletRequest request) {
        return service.listMine(userId(request));
    }

    @GetMapping("/tenants/{tenantId}/members")
    List<TenantService.MemberView> members(@PathVariable String tenantId, HttpServletRequest request) {
        return service.listMembers(userId(request), tenantId);
    }

    @PostMapping("/tenants/{tenantId}/invitations")
    TenantService.InvitationView invite(@PathVariable String tenantId, @RequestBody InviteRequest body,
                                        HttpServletRequest request) {
        return service.invite(userId(request), tenantId, body.login(), body.roleCode());
    }

    @PostMapping("/invitations/{invitationId}/accept")
    Map<String, Object> accept(@PathVariable String invitationId, HttpServletRequest request) {
        service.acceptInvitation(userId(request), invitationId);
        return Map.of();
    }

    @GetMapping("/invitations/{invitationId}")
    TenantService.InvitationView invitation(@PathVariable String invitationId, HttpServletRequest request) {
        return service.getInvitation(userId(request), invitationId);
    }

    @PostMapping("/invitations/{invitationId}/reject")
    Map<String, Object> reject(@PathVariable String invitationId, HttpServletRequest request) {
        service.rejectInvitation(userId(request), invitationId);
        return Map.of();
    }

    @DeleteMapping("/invitations/{invitationId}")
    Map<String, Object> revoke(@PathVariable String invitationId, HttpServletRequest request) {
        service.revokeInvitation(userId(request), invitationId);
        return Map.of();
    }

    @PostMapping("/invitations/{invitationId}/revoke")
    Map<String, Object> revokeByPost(@PathVariable String invitationId, HttpServletRequest request) {
        service.revokeInvitation(userId(request), invitationId);
        return Map.of();
    }

    @PatchMapping("/tenants/{tenantId}/members/{memberId}/role")
    Map<String, Object> changeRole(@PathVariable String tenantId, @PathVariable String memberId,
                                   @RequestBody ChangeRoleRequest body, HttpServletRequest request) {
        service.updateRole(userId(request), tenantId, memberId, body.roleCode());
        return Map.of();
    }

    @DeleteMapping("/tenants/{tenantId}/members/{memberId}")
    Map<String, Object> remove(@PathVariable String tenantId, @PathVariable String memberId,
                               HttpServletRequest request) {
        service.removeMember(userId(request), tenantId, memberId);
        return Map.of();
    }

    @PostMapping("/tenants/{tenantId}/leave")
    Map<String, Object> leave(@PathVariable String tenantId, HttpServletRequest request) {
        service.leave(userId(request), tenantId);
        return Map.of();
    }

    private String userId(HttpServletRequest request) {
        Object value = request.getAttribute("iamPrincipal");
        if (value instanceof IdentityService.SessionPrincipal principal) {
            return principal.user().id();
        }
        throw new IamException("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "需要登录");
    }

    record CreateTenantRequest(String name) { }
    record InviteRequest(String login, String roleCode) { }
    record ChangeRoleRequest(String roleCode) { }
}

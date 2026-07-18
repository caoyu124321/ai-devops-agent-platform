package devops.iam.event;

import devops.iam.contract.IamEvent;
import java.time.Instant;

/** 授权项撤销后的最小广播载荷，不包含敏感凭据或资源配置。 */
public record AuthorizationRevokedEvent(String tenantId, String subjectId, String resourceType,
                                        String resourceId, String actionCode, Instant occurredAt) implements IamEvent {
    @Override
    public String type() {
        return "AuthorizationRevoked";
    }
}

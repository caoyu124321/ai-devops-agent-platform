package devops.iam.event;

import devops.iam.contract.IamEvent;
import java.time.Instant;

/** 角色变更提交后的通知，消费者应自行决定是否影响运行中的任务。 */
public record RoleChangedEvent(String tenantId, String userId, String previousRole, String currentRole,
                               Instant occurredAt) implements IamEvent {
    @Override
    public String type() {
        return "RoleChanged";
    }
}

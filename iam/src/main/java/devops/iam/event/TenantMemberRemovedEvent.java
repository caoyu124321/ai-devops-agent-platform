package devops.iam.event;

import devops.iam.contract.IamEvent;
import java.time.Instant;

/** 成员撤销后供调度模块重新评估未完成任务的通知。 */
public record TenantMemberRemovedEvent(String tenantId, String userId, Instant occurredAt) implements IamEvent {
    @Override
    public String type() {
        return "TenantMemberRemoved";
    }
}

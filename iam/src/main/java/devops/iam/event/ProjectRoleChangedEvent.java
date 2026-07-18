package devops.iam.event;

import devops.iam.contract.IamEvent;
import java.time.Instant;

/** 项目范围角色变更后广播，由后续调度模块自行决定是否重新鉴权或停止任务。 */
public record ProjectRoleChangedEvent(String tenantId, String userId, String projectId, String roleCode,
                                      Instant occurredAt) implements IamEvent {
    @Override
    public String type() {
        return "ProjectRoleChanged";
    }
}

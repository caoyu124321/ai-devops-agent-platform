package devops.iam.event;

import devops.iam.contract.IamEvent;
import java.time.Instant;

/** 密码已更新并撤销会话后的最小通知，绝不包含密码或 Token。 */
public record PasswordChangedEvent(String userId, Instant occurredAt) implements IamEvent {
    @Override
    public String type() {
        return "PasswordChanged";
    }
}

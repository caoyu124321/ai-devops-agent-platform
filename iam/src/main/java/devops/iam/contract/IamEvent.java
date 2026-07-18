package devops.iam.contract;

import java.time.Instant;

/** IAM 广播事件的最小公开契约。 */
public interface IamEvent {
    String type();

    Instant occurredAt();
}

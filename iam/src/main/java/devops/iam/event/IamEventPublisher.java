package devops.iam.event;

import devops.iam.contract.IamEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 仅在事务成功提交后进行进程内广播，避免下游收到未落库的授权变更。 */
@Component
public class IamEventPublisher {
    private final ApplicationEventPublisher publisher;

    public IamEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishAfterCommit(IamEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            publisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishEvent(event);
            }
        });
    }
}

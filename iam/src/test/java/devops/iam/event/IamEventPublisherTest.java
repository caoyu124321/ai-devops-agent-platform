package devops.iam.event;

import devops.iam.contract.IamEvent;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 验证事件只在事务提交后广播，避免下游观察到未提交的授权状态。 */
class IamEventPublisherTest {
    @AfterEach
    void cleanTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldPublishOnlyAfterTransactionCommit() {
        ApplicationEventPublisher applicationPublisher = mock(ApplicationEventPublisher.class);
        IamEventPublisher publisher = new IamEventPublisher(applicationPublisher);
        IamEvent event = new PasswordChangedEvent("user-1", Instant.now());
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publishAfterCommit(event);

        org.mockito.Mockito.verifyNoInteractions(applicationPublisher);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(applicationPublisher).publishEvent(event);
    }
}

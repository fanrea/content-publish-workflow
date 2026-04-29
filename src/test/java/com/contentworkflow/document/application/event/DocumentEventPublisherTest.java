package com.contentworkflow.document.application.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentEventPublisherTest {

    private final RecordingPublisher publisher = new RecordingPublisher();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishAfterCommit_shouldPublishImmediatelyWithoutTransaction() {
        publisher.publishAfterCommit(newEvent());

        assertThat(publisher.publishCount.get()).isEqualTo(1);
    }

    @Test
    void publishAfterCommit_shouldPublishOnlyAfterCommitWhenTransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            publisher.publishAfterCommit(newEvent());
            assertThat(publisher.publishCount.get()).isZero();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCommit());

            assertThat(publisher.publishCount.get()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void publishAfterCommit_shouldNotPublishWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            publisher.publishAfterCommit(newEvent());
            assertThat(publisher.publishCount.get()).isZero();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(publisher.publishCount.get()).isZero();
    }

    private DocumentDomainEvent newEvent() {
        return DocumentDomainEvent.of(
                "TEST_EVENT",
                1L,
                1,
                "u1",
                "alice",
                Map.of("k", "v")
        );
    }

    private static final class RecordingPublisher implements DocumentEventPublisher {

        private final AtomicInteger publishCount = new AtomicInteger();

        @Override
        public void publish(DocumentDomainEvent event) {
            publishCount.incrementAndGet();
        }
    }
}

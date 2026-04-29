package com.contentworkflow.document.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReliableDocumentEventPublisherTest {

    @Test
    void publish_shouldPersistFailure_thenRetrySuccessfully() {
        ControlledTransport transport = new ControlledTransport();
        transport.fail = true;
        InMemoryFailedStore store = new InMemoryFailedStore();
        ReliableDocumentEventPublisher publisher = new ReliableDocumentEventPublisher(
                transport,
                store,
                new ObjectMapper().findAndRegisterModules(),
                false,
                1000,
                10,
                30000,
                5,
                100,
                1000
        );
        DocumentDomainEvent event = buildEvent("DOCUMENT_OPERATION_APPLIED", 1L, 2);

        publisher.publish(event);

        assertThat(store.events).hasSize(1);
        assertThat(transport.sendCount.get()).isEqualTo(1);

        transport.fail = false;
        int processed = publisher.retryOnce();
        assertThat(processed).isEqualTo(1);
        assertThat(transport.sendCount.get()).isEqualTo(2);
        assertThat(store.events).allMatch(e -> e.status.equals("SENT"));
    }

    @Test
    void retry_shouldMarkDeadAfterMaxAttempts() throws Exception {
        ControlledTransport transport = new ControlledTransport();
        transport.fail = true;
        InMemoryFailedStore store = new InMemoryFailedStore();
        ReliableDocumentEventPublisher publisher = new ReliableDocumentEventPublisher(
                transport,
                store,
                new ObjectMapper().findAndRegisterModules(),
                false,
                1000,
                10,
                30000,
                2,
                100,
                1000
        );
        DocumentDomainEvent event = buildEvent("DOCUMENT_EDITED", 2L, 3);

        publisher.publish(event);
        assertThat(store.events).hasSize(1);

        publisher.retryOnce();
        assertThat(store.events.get(0).status).isEqualTo("RETRYING");
        assertThat(store.events.get(0).attemptCount).isEqualTo(1);

        Thread.sleep(150);
        publisher.retryOnce();
        assertThat(store.events.get(0).status).isEqualTo("DEAD");
        assertThat(store.events.get(0).attemptCount).isEqualTo(2);
    }

    private DocumentDomainEvent buildEvent(String eventType, Long docId, Integer revision) {
        return new DocumentDomainEvent(
                eventType,
                docId,
                revision,
                "u1",
                "alice",
                Map.of("k", "v"),
                LocalDateTime.now()
        );
    }

    private static final class ControlledTransport implements DocumentEventTransport {
        private final AtomicInteger sendCount = new AtomicInteger();
        private volatile boolean fail;

        @Override
        public void send(DocumentDomainEvent event) {
            sendCount.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("send failed");
            }
        }
    }

    private static final class InMemoryFailedStore implements FailedDocumentEventStore {
        private final List<EventState> events = new ArrayList<>();
        private long idSeq = 1L;

        @Override
        public synchronized void saveFailure(String eventKey, DocumentDomainEvent event, String errorMessage, LocalDateTime now) {
            EventState existing = findByEventKey(eventKey);
            if (existing == null) {
                EventState created = new EventState();
                created.id = idSeq++;
                created.eventKey = eventKey;
                created.event = event;
                created.status = "PENDING";
                created.attemptCount = 0;
                created.nextRetryAt = now;
                created.lastError = errorMessage;
                events.add(created);
                return;
            }
            if ("SENT".equals(existing.status)) {
                return;
            }
            existing.status = "PENDING";
            existing.nextRetryAt = now;
            existing.lastError = errorMessage;
            existing.lockedAt = null;
            existing.lockedBy = null;
        }

        @Override
        public synchronized List<StoredDocumentEvent> claimBatch(LocalDateTime now, String lockerId, long lockTimeoutMs, int limit) {
            LocalDateTime lockExpiredAt = now.minusNanos(lockTimeoutMs * 1_000_000L);
            List<StoredDocumentEvent> claimed = new ArrayList<>();
            for (EventState state : events) {
                if (claimed.size() >= limit) {
                    break;
                }
                if (!"PENDING".equals(state.status) && !"RETRYING".equals(state.status)) {
                    continue;
                }
                if (state.nextRetryAt != null && state.nextRetryAt.isAfter(now)) {
                    continue;
                }
                if (state.lockedAt != null && !state.lockedAt.isBefore(lockExpiredAt)) {
                    continue;
                }
                state.lockedBy = lockerId;
                state.lockedAt = now;
                claimed.add(new StoredDocumentEvent(state.id, state.eventKey, state.event, state.attemptCount));
            }
            return claimed;
        }

        @Override
        public synchronized void markSent(Long id, LocalDateTime now) {
            EventState state = findById(id);
            state.status = "SENT";
            state.lockedAt = null;
            state.lockedBy = null;
            state.lastError = null;
        }

        @Override
        public synchronized void markRetry(Long id, int nextAttemptCount, LocalDateTime nextRetryAt, String errorMessage, boolean dead) {
            EventState state = findById(id);
            state.status = dead ? "DEAD" : "RETRYING";
            state.attemptCount = nextAttemptCount;
            state.nextRetryAt = nextRetryAt;
            state.lastError = errorMessage;
            state.lockedAt = null;
            state.lockedBy = null;
        }

        private EventState findByEventKey(String eventKey) {
            for (EventState state : events) {
                if (state.eventKey.equals(eventKey)) {
                    return state;
                }
            }
            return null;
        }

        private EventState findById(Long id) {
            for (EventState state : events) {
                if (state.id.equals(id)) {
                    return state;
                }
            }
            throw new IllegalArgumentException("event not found");
        }
    }

    private static final class EventState {
        private Long id;
        private String eventKey;
        private DocumentDomainEvent event;
        private String status;
        private int attemptCount;
        private LocalDateTime nextRetryAt;
        private String lastError;
        private String lockedBy;
        private LocalDateTime lockedAt;
    }
}

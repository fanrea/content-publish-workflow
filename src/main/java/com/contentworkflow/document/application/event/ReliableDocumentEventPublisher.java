package com.contentworkflow.document.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Primary
public class ReliableDocumentEventPublisher implements DocumentEventPublisher, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ReliableDocumentEventPublisher.class);

    private final DocumentEventTransport transport;
    private final FailedDocumentEventStore failedEventStore;
    private final ObjectMapper objectMapper;
    private final boolean retryEnabled;
    private final long retryIntervalMs;
    private final int fetchLimit;
    private final long lockTimeoutMs;
    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final String lockerId;
    private volatile ScheduledExecutorService retryExecutor;

    public ReliableDocumentEventPublisher(DocumentEventTransport transport,
                                          FailedDocumentEventStore failedEventStore,
                                          ObjectMapper objectMapper,
                                          @Value("${workflow.event.reliable.retry-enabled:true}") boolean retryEnabled,
                                          @Value("${workflow.event.reliable.retry-interval-ms:3000}") long retryIntervalMs,
                                          @Value("${workflow.event.reliable.fetch-limit:50}") int fetchLimit,
                                          @Value("${workflow.event.reliable.lock-timeout-ms:30000}") long lockTimeoutMs,
                                          @Value("${workflow.event.reliable.max-attempts:16}") int maxAttempts,
                                          @Value("${workflow.event.reliable.base-backoff-ms:500}") long baseBackoffMs,
                                          @Value("${workflow.event.reliable.max-backoff-ms:60000}") long maxBackoffMs) {
        this.transport = transport;
        this.failedEventStore = failedEventStore;
        this.objectMapper = objectMapper;
        this.retryEnabled = retryEnabled;
        this.retryIntervalMs = Math.max(200L, retryIntervalMs);
        this.fetchLimit = Math.max(1, fetchLimit);
        this.lockTimeoutMs = Math.max(1000L, lockTimeoutMs);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMs = Math.max(100L, baseBackoffMs);
        this.maxBackoffMs = Math.max(this.baseBackoffMs, maxBackoffMs);
        this.lockerId = "doc-event-retry-" + UUID.randomUUID();
    }

    @Override
    public void afterPropertiesSet() {
        if (!retryEnabled) {
            return;
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "doc-event-retry-worker");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        this.retryExecutor = executor;
        executor.scheduleWithFixedDelay(this::drainSafely, retryIntervalMs, retryIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void publish(DocumentDomainEvent event) {
        if (event == null) {
            return;
        }
        try {
            transport.send(event);
        } catch (Exception ex) {
            try {
                String eventKey = buildEventKey(event);
                failedEventStore.saveFailure(eventKey, event, ex.getMessage(), LocalDateTime.now());
                log.warn("document event publish failed, persisted for retry, eventType={}, documentId={}, eventKey={}",
                        event.eventType(),
                        event.documentId(),
                        eventKey,
                        ex);
            } catch (Exception persistEx) {
                log.error("document event publish failed and persistence failed, eventType={}, documentId={}",
                        event.eventType(),
                        event.documentId(),
                        persistEx);
            }
        }
    }

    int retryOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<StoredDocumentEvent> events = failedEventStore.claimBatch(
                now,
                lockerId,
                lockTimeoutMs,
                fetchLimit
        );
        if (events.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (StoredDocumentEvent stored : events) {
            try {
                transport.send(stored.event());
                failedEventStore.markSent(stored.id(), LocalDateTime.now());
            } catch (Exception ex) {
                int nextAttemptCount = stored.attemptCount() + 1;
                boolean dead = nextAttemptCount >= maxAttempts;
                LocalDateTime nextRetryAt = dead
                        ? null
                        : LocalDateTime.now().plusNanos(computeBackoffMs(nextAttemptCount) * 1_000_000L);
                failedEventStore.markRetry(
                        stored.id(),
                        nextAttemptCount,
                        nextRetryAt,
                        ex.getMessage(),
                        dead
                );
                log.warn("document event retry failed, eventType={}, documentId={}, eventKey={}, attempt={}, dead={}",
                        stored.event().eventType(),
                        stored.event().documentId(),
                        stored.eventKey(),
                        nextAttemptCount,
                        dead,
                        ex);
            }
            processed++;
        }
        return processed;
    }

    @Override
    public void destroy() {
        ScheduledExecutorService executor = this.retryExecutor;
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        this.retryExecutor = null;
    }

    private void drainSafely() {
        try {
            retryOnce();
        } catch (Exception ex) {
            log.warn("document event retry loop failed", ex);
        }
    }

    private long computeBackoffMs(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 16));
        long multiplier = 1L << exponent;
        long backoff = baseBackoffMs * multiplier;
        return Math.min(backoff, maxBackoffMs);
    }

    private String buildEventKey(DocumentDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to build event key", ex);
        }
    }
}

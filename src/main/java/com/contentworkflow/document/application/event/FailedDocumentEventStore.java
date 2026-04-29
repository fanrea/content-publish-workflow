package com.contentworkflow.document.application.event;

import java.time.LocalDateTime;
import java.util.List;

public interface FailedDocumentEventStore {

    void saveFailure(String eventKey, DocumentDomainEvent event, String errorMessage, LocalDateTime now);

    List<StoredDocumentEvent> claimBatch(LocalDateTime now, String lockerId, long lockTimeoutMs, int limit);

    void markSent(Long id, LocalDateTime now);

    void markRetry(Long id, int nextAttemptCount, LocalDateTime nextRetryAt, String errorMessage, boolean dead);
}

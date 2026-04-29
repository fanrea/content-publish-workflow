package com.contentworkflow.document.application.event;

public record StoredDocumentEvent(
        Long id,
        String eventKey,
        DocumentDomainEvent event,
        int attemptCount
) {
    public StoredDocumentEvent withAttemptCount(int nextAttemptCount) {
        return new StoredDocumentEvent(id, eventKey, event, nextAttemptCount);
    }
}

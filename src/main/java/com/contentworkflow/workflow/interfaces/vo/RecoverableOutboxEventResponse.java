package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;

import java.time.LocalDateTime;

/**
 * Recoverable outbox event view for operations.
 */
public record RecoverableOutboxEventResponse(
        Long id,
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Integer aggregateVersion,
        OutboxEventStatus status,
        int attempt,
        String errorMessage,
        LocalDateTime nextRetryAt,
        String lockedBy,
        LocalDateTime lockedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean recoverable
) {
}

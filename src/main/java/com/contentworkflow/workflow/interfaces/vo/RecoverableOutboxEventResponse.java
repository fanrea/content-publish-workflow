package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
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

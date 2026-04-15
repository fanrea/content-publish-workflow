package com.contentworkflow.common.messaging;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 领域事件（用于跨模块/跨服务传播）。
 *
 * <p>注意：这里的事件不是为了“实时强一致”，而是用于 outbox + MQ 的最终一致性编排。</p>
 */
public record WorkflowEvent(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Integer aggregateVersion,
        Object payload,
        Map<String, Object> headers,
        LocalDateTime occurredAt
) {

    public static WorkflowEvent of(String eventType,
                                   String aggregateType,
                                   String aggregateId,
                                   Integer aggregateVersion,
                                   Object payload,
                                   Map<String, Object> headers) {
        return new WorkflowEvent(
                UUID.randomUUID().toString(),
                Objects.requireNonNull(eventType, "eventType"),
                Objects.requireNonNull(aggregateType, "aggregateType"),
                Objects.requireNonNull(aggregateId, "aggregateId"),
                aggregateVersion,
                payload,
                headers,
                LocalDateTime.now()
        );
    }
}


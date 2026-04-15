package com.contentworkflow.common.messaging.outbox;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.common.messaging.WorkflowMessagingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Outbox publisher: persist events into the outbox table, then let the relay deliver them to MQ.
 */
public class OutboxWorkflowEventPublisher implements WorkflowEventPublisher {

    private final WorkflowMessagingProperties props;
    private final RoutingKeyResolver routingKeyResolver;
    private final ObjectMapper objectMapper;
    private final OutboxEnqueuer enqueuer;

    public OutboxWorkflowEventPublisher(WorkflowMessagingProperties props,
                                        RoutingKeyResolver routingKeyResolver,
                                        ObjectMapper objectMapper,
                                        OutboxEnqueuer enqueuer) {
        this.props = props;
        this.routingKeyResolver = routingKeyResolver;
        this.objectMapper = objectMapper;
        this.enqueuer = enqueuer;
    }

    @Override
    public void publish(WorkflowEvent event) {
        OutboxEventEntity row = new OutboxEventEntity();
        row.setEventId(event.eventId());
        row.setEventType(event.eventType());
        row.setAggregateType(event.aggregateType());
        row.setAggregateId(event.aggregateId());
        row.setAggregateVersion(event.aggregateVersion());
        row.setExchangeName(props.getExchange());
        row.setRoutingKey(routingKeyResolver.resolve(event.eventType()));
        row.setStatus(OutboxEventStatus.NEW);
        row.setAttempt(0);
        row.setNextRetryAt(null);
        row.setLockedAt(null);
        row.setLockedBy(null);
        row.setErrorMessage(null);
        row.setSentAt(null);
        // createdAt/updatedAt handled by @PrePersist

        row.setPayloadJson(toJsonQuietly(event.payload()));
        row.setHeadersJson(toJsonQuietly(event.headers()));

        enqueuer.enqueue(row);
    }

    private String toJsonQuietly(Object obj) {
        if (obj == null) {
            return "{}";
        }
        if (obj instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            // Do not break the main flow due to serialization errors. Store minimal info for debugging.
            return "{\"_serializeError\":true,\"_type\":\"" + obj.getClass().getName() + "\"}";
        }
    }
}

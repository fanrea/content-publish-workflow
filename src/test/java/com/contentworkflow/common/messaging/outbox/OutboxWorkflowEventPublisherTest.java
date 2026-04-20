package com.contentworkflow.common.messaging.outbox;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowMessagingProperties;
import com.contentworkflow.testing.WorkflowMdcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxWorkflowEventPublisherTest {

    @Test
    void publish_shouldPersistTraceAndRequestIdsAlongsideHeaders() throws Exception {
        WorkflowMessagingProperties properties = new WorkflowMessagingProperties();
        properties.setExchange("workflow.exchange");
        RoutingKeyResolver routingKeyResolver = eventType -> "workflow.content.published";
        OutboxEnqueuer enqueuer = mock(OutboxEnqueuer.class);
        OutboxWorkflowEventPublisher publisher = new OutboxWorkflowEventPublisher(
                properties,
                routingKeyResolver,
                new ObjectMapper(),
                enqueuer
        );

        WorkflowMdcTestSupport.withLoggingContext("trace-outbox-mdc-1001", "req-outbox-mdc-1001", () -> {
            WorkflowEvent event = WorkflowEvent.of(
                    "CONTENT_PUBLISHED",
                    "article",
                    "A-1001",
                    3,
                    Map.of("published", true),
                    Map.of("source", "test")
            );

            publisher.publish(event);
        });

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(enqueuer).enqueue(captor.capture());
        OutboxEventEntity row = captor.getValue();
        assertEquals("trace-outbox-mdc-1001", row.getTraceId());
        assertEquals("req-outbox-mdc-1001", row.getRequestId());
        assertTrue(row.getHeadersJson().contains("\"X-Trace-Id\":\"trace-outbox-mdc-1001\""));
        assertTrue(row.getHeadersJson().contains("\"X-Request-Id\":\"req-outbox-mdc-1001\""));
    }
}

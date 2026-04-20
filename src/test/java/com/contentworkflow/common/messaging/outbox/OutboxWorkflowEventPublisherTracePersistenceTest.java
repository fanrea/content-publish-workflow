package com.contentworkflow.common.messaging.outbox;

import com.contentworkflow.common.logging.WorkflowLogContext;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowMessagingProperties;
import com.contentworkflow.testing.WorkflowMdcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxWorkflowEventPublisherTracePersistenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publish_shouldPersistCurrentTraceHeadersIntoOutboxRow() throws Exception {
        WorkflowMessagingProperties properties = new WorkflowMessagingProperties();
        properties.setExchange("workflow.exchange");
        AtomicReference<OutboxEventEntity> enqueued = new AtomicReference<>();
        OutboxWorkflowEventPublisher publisher = new OutboxWorkflowEventPublisher(
                properties,
                eventType -> "workflow.content.published",
                objectMapper,
                enqueued::set
        );

        WorkflowMdcTestSupport.withLoggingContext("trace-outbox-1001", "req-outbox-1001", () ->
                publisher.publish(WorkflowEvent.of(
                        "CONTENT_PUBLISHED",
                        "article",
                        "A-1001",
                        1,
                        Map.of("title", "draft"),
                        Map.of()
                )));

        OutboxEventEntity saved = enqueued.get();
        assertThat(saved).isNotNull();
        assertThat(saved.getHeadersJson()).contains("\"" + WorkflowLogContext.TRACE_ID_HEADER + "\":\"trace-outbox-1001\"");
        assertThat(saved.getHeadersJson()).contains("\"" + WorkflowLogContext.REQUEST_ID_HEADER + "\":\"req-outbox-1001\"");
        assertThat(saved.getHeadersJson()).contains("\"" + WorkflowLogContext.TRACE_ID_KEY + "\":\"trace-outbox-1001\"");
        assertThat(saved.getHeadersJson()).contains("\"" + WorkflowLogContext.REQUEST_ID_KEY + "\":\"req-outbox-1001\"");
    }
}

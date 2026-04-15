package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Demo consumers for publish side-effect events.
 */
@Component
@ConditionalOnProperty(prefix = "workflow.outbox.topology.consumer", name = "enabled", havingValue = "true")
public class WorkflowSideEffectEventLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSideEffectEventLoggingListener.class);

    private final ObjectMapper objectMapper;
    private final WorkflowMessageDeduplicationGuard deduplicationGuard;
    private final WorkflowSideEffectConsumerService consumerService;

    public WorkflowSideEffectEventLoggingListener(ObjectMapper objectMapper,
                                                  WorkflowMessageDeduplicationGuard deduplicationGuard,
                                                  WorkflowSideEffectConsumerService consumerService) {
        this.objectMapper = objectMapper;
        this.deduplicationGuard = deduplicationGuard;
        this.consumerService = consumerService;
    }

    @RabbitListener(queues = "${workflow.outbox.topology.search-index-queue:cpw.workflow.search-index.refresh}")
    public void onSearchIndexRefreshRequested(String payload,
                                              @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
                                              @Header(name = "x-event-type", required = false) String eventType,
                                              @Headers Map<String, Object> headers) {
        if (!deduplicationGuard.firstConsume(messageId)) {
            log.info("skip duplicate search-index event messageId={} type={}", messageId, eventType);
            return;
        }
        PublishTaskEventFactory.SearchIndexRefreshRequestedPayload eventPayload =
                readPayload(payload, PublishTaskEventFactory.SearchIndexRefreshRequestedPayload.class);
        consumerService.acceptSearchIndexRefresh(messageId, eventPayload);
        log.info("received search-index side-effect event messageId={} type={} headers={}", messageId, eventType, headers);
    }

    @RabbitListener(queues = "${workflow.outbox.topology.read-model-queue:cpw.workflow.read-model.sync}")
    public void onReadModelSyncRequested(String payload,
                                         @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
                                         @Header(name = "x-event-type", required = false) String eventType,
                                         @Headers Map<String, Object> headers) {
        if (!deduplicationGuard.firstConsume(messageId)) {
            log.info("skip duplicate read-model event messageId={} type={}", messageId, eventType);
            return;
        }
        PublishTaskEventFactory.ReadModelSyncRequestedPayload eventPayload =
                readPayload(payload, PublishTaskEventFactory.ReadModelSyncRequestedPayload.class);
        consumerService.acceptReadModelSync(messageId, eventPayload);
        log.info("received read-model side-effect event messageId={} type={} headers={}", messageId, eventType, headers);
    }

    @RabbitListener(queues = "${workflow.outbox.topology.notification-queue:cpw.workflow.publish.notification}")
    public void onNotificationRequested(String payload,
                                        @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
                                        @Header(name = "x-event-type", required = false) String eventType,
                                        @Headers Map<String, Object> headers) {
        if (!deduplicationGuard.firstConsume(messageId)) {
            log.info("skip duplicate notification event messageId={} type={}", messageId, eventType);
            return;
        }
        PublishTaskEventFactory.PublishNotificationRequestedPayload eventPayload =
                readPayload(payload, PublishTaskEventFactory.PublishNotificationRequestedPayload.class);
        consumerService.acceptPublishNotification(messageId, eventPayload);
        log.info("received notification side-effect event messageId={} type={} headers={}", messageId, eventType, headers);
    }

    private <T> T readPayload(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid MQ payload for " + type.getSimpleName(), e);
        }
    }
}

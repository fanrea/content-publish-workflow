package com.contentworkflow.document.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "workflow.event.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqDocumentEventPublisher implements DocumentEventTransport, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDocumentEventPublisher.class);

    private final ObjectMapper objectMapper;
    private final DefaultMQProducer producer;
    private final String topic;
    private final boolean useSyncSend;
    private final int maxRetryTimes;
    private final long retryBackoffMs;
    private volatile boolean started;

    public RocketMqDocumentEventPublisher(ObjectMapper objectMapper,
                                          @Value("${workflow.event.rocketmq.name-server:}") String nameServer,
                                          @Value("${workflow.event.rocketmq.producer-group:cpw_doc_event_producer}") String producerGroup,
                                          @Value("${workflow.event.rocketmq.topic:cpw_doc_event}") String topic,
                                          @Value("${workflow.event.rocketmq.send-timeout-ms:3000}") int sendTimeoutMs,
                                          @Value("${workflow.event.rocketmq.delivery-mode:SYNC}") String deliveryMode,
                                          @Value("${workflow.event.rocketmq.max-retries:2}") int maxRetries,
                                          @Value("${workflow.event.rocketmq.retry-backoff-ms:100}") long retryBackoffMs) {
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.useSyncSend = !"ONEWAY".equalsIgnoreCase(deliveryMode);
        this.maxRetryTimes = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
        this.producer = new DefaultMQProducer(producerGroup);
        this.producer.setNamesrvAddr(nameServer);
        this.producer.setSendMsgTimeout(Math.max(1000, sendTimeoutMs));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (producer.getNamesrvAddr() == null || producer.getNamesrvAddr().isBlank()) {
            throw new IllegalStateException("workflow.event.rocketmq.name-server must be configured when rocketmq is enabled");
        }
        producer.start();
        started = true;
    }

    @Override
    public void send(DocumentDomainEvent event) throws Exception {
        if (!started || event == null) {
            return;
        }
        Message message = new Message(
                topic,
                event.eventType(),
                objectMapper.writeValueAsBytes(event)
        );
        if (useSyncSend) {
            publishSyncWithRetry(message, event);
        } else {
            producer.sendOneway(message);
        }
    }

    private void publishSyncWithRetry(Message message, DocumentDomainEvent event) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetryTimes; attempt++) {
            try {
                SendResult result = producer.send(message);
                if (result != null && result.getSendStatus() == SendStatus.SEND_OK) {
                    return;
                }
                log.warn("rocketmq publish status not ok, attempt={}, status={}, eventType={}, documentId={}",
                        attempt + 1,
                        result == null ? null : result.getSendStatus(),
                        event.eventType(),
                        event.documentId());
            } catch (Exception ex) {
                lastException = ex;
                log.warn("rocketmq publish attempt failed, attempt={}, eventType={}, documentId={}",
                        attempt + 1,
                        event.eventType(),
                        event.documentId(),
                        ex);
            }
            if (attempt < maxRetryTimes && retryBackoffMs > 0) {
                try {
                    Thread.sleep(retryBackoffMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("rocketmq publish failed: send status not ok");
    }

    @Override
    public void destroy() {
        if (!started) {
            return;
        }
        try {
            producer.shutdown();
        } finally {
            started = false;
        }
    }
}

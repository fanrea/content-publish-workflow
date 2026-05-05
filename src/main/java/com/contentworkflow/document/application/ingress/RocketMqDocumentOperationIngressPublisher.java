package com.contentworkflow.document.application.ingress;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
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

import java.util.Objects;

@Service
@ConditionalOnProperty(prefix = "workflow.ingress.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqDocumentOperationIngressPublisher implements DocumentOperationIngressPublisher, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDocumentOperationIngressPublisher.class);
    private static final String MESSAGE_TAG = "DOCUMENT_OPERATION_INGRESS";
    private static final MessageQueueSelector DOC_ID_QUEUE_SELECTOR = (queues, msg, arg) -> {
        if (queues == null || queues.isEmpty()) {
            throw new IllegalStateException("rocketmq has no writable queue for ingress topic");
        }
        int queueIndex = resolveQueueIndex((Long) arg, queues.size());
        return queues.get(queueIndex);
    };

    private final ObjectMapper objectMapper;
    private final DefaultMQProducer producer;
    private final String topic;
    private final int maxRetryTimes;
    private final long retryBackoffMs;
    private volatile boolean started;

    public RocketMqDocumentOperationIngressPublisher(
            ObjectMapper objectMapper,
            @Value("${workflow.ingress.rocketmq.name-server:}") String nameServer,
            @Value("${workflow.ingress.rocketmq.producer-group:cpw_doc_ingress_producer}") String producerGroup,
            @Value("${workflow.ingress.rocketmq.topic:cpw_doc_ingress}") String topic,
            @Value("${workflow.ingress.rocketmq.send-timeout-ms:1000}") int sendTimeoutMs,
            @Value("${workflow.ingress.rocketmq.max-retries:2}") int maxRetries,
            @Value("${workflow.ingress.rocketmq.retry-backoff-ms:50}") long retryBackoffMs) {
        this(
                objectMapper,
                buildProducer(nameServer, producerGroup, sendTimeoutMs),
                topic,
                maxRetries,
                retryBackoffMs
        );
    }

    RocketMqDocumentOperationIngressPublisher(
            ObjectMapper objectMapper,
            DefaultMQProducer producer,
            String topic,
            int maxRetries,
            long retryBackoffMs) {
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.topic = topic;
        this.maxRetryTimes = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
    }

    private static DefaultMQProducer buildProducer(String nameServer, String producerGroup, int sendTimeoutMs) {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(Math.max(500, sendTimeoutMs));
        return producer;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (producer.getNamesrvAddr() == null || producer.getNamesrvAddr().isBlank()) {
            throw new IllegalStateException("workflow.ingress.rocketmq.name-server must be configured when ingress rocketmq is enabled");
        }
        producer.start();
        started = true;
    }

    @Override
    public void publish(DocumentOperationIngressCommand command) throws Exception {
        DocumentOperationIngressCommand normalized = Objects.requireNonNull(command, "command must not be null");
        if (!started) {
            throw new IllegalStateException("document operation ingress rocketmq producer is not started");
        }
        Objects.requireNonNull(normalized.docId(), "command.docId must not be null");
        Message message = new Message(
                topic,
                MESSAGE_TAG,
                objectMapper.writeValueAsBytes(normalized)
        );
        message.setKeys(buildMessageKey(normalized));
        publishWithRetry(message, normalized);
    }

    private void publishWithRetry(Message message, DocumentOperationIngressCommand command) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetryTimes; attempt++) {
            try {
                SendResult result = producer.send(message, DOC_ID_QUEUE_SELECTOR, command.docId());
                if (result != null && result.getSendStatus() == SendStatus.SEND_OK) {
                    return;
                }
                log.warn("ingress publish status not ok, attempt={}, status={}, docId={}, clientSeq={}",
                        attempt + 1,
                        result == null ? null : result.getSendStatus(),
                        command.docId(),
                        command.clientSeq());
            } catch (Exception ex) {
                lastException = ex;
                log.warn("ingress publish attempt failed, attempt={}, docId={}, clientSeq={}",
                        attempt + 1,
                        command.docId(),
                        command.clientSeq(),
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
        throw new IllegalStateException("ingress publish failed: send status not ok");
    }

    static int resolveQueueIndex(Long docId, int queueCount) {
        if (queueCount <= 0) {
            throw new IllegalArgumentException("queueCount must be positive");
        }
        int hash = docId == null ? 0 : docId.hashCode();
        return Math.floorMod(hash, queueCount);
    }

    private String buildMessageKey(DocumentOperationIngressCommand command) {
        return command.docId() + ":" + command.sessionId() + ":" + command.clientSeq();
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

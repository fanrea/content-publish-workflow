package com.contentworkflow.document.application.gc;

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

import java.time.Instant;
import java.util.Objects;

@Service
@ConditionalOnProperty(prefix = "workflow.gc.compaction.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqDocumentCompactionTaskPublisher implements DocumentCompactionTaskPublisher, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDocumentCompactionTaskPublisher.class);
    private static final String MESSAGE_TAG = "DOCUMENT_COMPACTION_TASK";

    private final ObjectMapper objectMapper;
    private final DefaultMQProducer producer;
    private final String topic;
    private final int maxRetryTimes;
    private final long retryBackoffMs;
    private volatile boolean started;

    public RocketMqDocumentCompactionTaskPublisher(
            ObjectMapper objectMapper,
            @Value("${workflow.gc.compaction.rocketmq.name-server:}") String nameServer,
            @Value("${workflow.gc.compaction.rocketmq.producer-group:cpw_doc_compaction_task_producer}") String producerGroup,
            @Value("${workflow.gc.compaction.rocketmq.topic:cpw_doc_compaction_task}") String topic,
            @Value("${workflow.gc.compaction.rocketmq.send-timeout-ms:1000}") int sendTimeoutMs,
            @Value("${workflow.gc.compaction.rocketmq.max-retries:2}") int maxRetries,
            @Value("${workflow.gc.compaction.rocketmq.retry-backoff-ms:50}") long retryBackoffMs) {
        this(
                objectMapper,
                buildProducer(nameServer, producerGroup, sendTimeoutMs),
                topic,
                maxRetries,
                retryBackoffMs
        );
    }

    RocketMqDocumentCompactionTaskPublisher(ObjectMapper objectMapper,
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
            throw new IllegalStateException(
                    "workflow.gc.compaction.rocketmq.name-server must be configured when compaction rocketmq is enabled"
            );
        }
        producer.start();
        started = true;
    }

    @Override
    public void publish(DocumentCompactionTask task) {
        DocumentCompactionTask normalized = Objects.requireNonNull(task, "task must not be null");
        if (!started) {
            throw new IllegalStateException("document compaction task rocketmq producer is not started");
        }
        Objects.requireNonNull(normalized.documentId(), "task.documentId must not be null");
        try {
            Message message = new Message(
                    topic,
                    MESSAGE_TAG,
                    objectMapper.writeValueAsBytes(normalized)
            );
            message.setKeys(buildMessageKey(normalized));
            publishWithRetry(message, normalized);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to publish compaction task", ex);
        }
    }

    private void publishWithRetry(Message message, DocumentCompactionTask task) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetryTimes; attempt++) {
            try {
                SendResult result = producer.send(message);
                if (result != null && result.getSendStatus() == SendStatus.SEND_OK) {
                    return;
                }
                log.warn("compaction task publish status not ok, attempt={}, status={}, documentId={}, trigger={}, createdAt={}",
                        attempt + 1,
                        result == null ? null : result.getSendStatus(),
                        task.documentId(),
                        task.trigger(),
                        task.createdAt());
            } catch (Exception ex) {
                lastException = ex;
                log.warn("compaction task publish attempt failed, attempt={}, documentId={}, trigger={}, createdAt={}",
                        attempt + 1,
                        task.documentId(),
                        task.trigger(),
                        task.createdAt(),
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
        throw new IllegalStateException("compaction task publish failed: send status not ok");
    }

    private String buildMessageKey(DocumentCompactionTask task) {
        Instant createdAt = task.createdAt() == null ? Instant.EPOCH : task.createdAt();
        String key = task.documentId() + ":" + task.trigger() + ":" + createdAt.toEpochMilli();
        if (task.segmentUpperClockInclusive() == null) {
            return key;
        }
        return key + ":" + task.segmentUpperClockInclusive();
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

package com.contentworkflow.document.application.ingress;

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
@ConditionalOnProperty(prefix = "workflow.ingress.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqDocumentOperationIngressPublisher implements DocumentOperationIngressPublisher, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDocumentOperationIngressPublisher.class);
    private static final String MESSAGE_TAG = "DOCUMENT_OPERATION_INGRESS";

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
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.maxRetryTimes = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
        this.producer = new DefaultMQProducer(producerGroup);
        this.producer.setNamesrvAddr(nameServer);
        this.producer.setSendMsgTimeout(Math.max(500, sendTimeoutMs));
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
        if (!started || command == null) {
            return;
        }
        Message message = new Message(
                topic,
                MESSAGE_TAG,
                objectMapper.writeValueAsBytes(command)
        );
        message.setKeys(buildMessageKey(command));
        publishWithRetry(message, command);
    }

    private void publishWithRetry(Message message, DocumentOperationIngressCommand command) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetryTimes; attempt++) {
            try {
                SendResult result = producer.send(message);
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

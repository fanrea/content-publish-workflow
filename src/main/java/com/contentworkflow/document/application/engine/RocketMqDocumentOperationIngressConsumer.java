package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ingress operation consumer: receives commands from RocketMQ and delegates to collaboration engine.
 */
@Component
@ConditionalOnProperty(prefix = "workflow.ingress.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqDocumentOperationIngressConsumer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDocumentOperationIngressConsumer.class);

    private final DefaultMQPushConsumer consumer;
    private final ObjectMapper objectMapper;
    private final DocumentOperationIngressHandler ingressHandler;
    private final String topic;
    private volatile boolean started;

    public RocketMqDocumentOperationIngressConsumer(
            ObjectMapper objectMapper,
            DocumentOperationIngressHandler ingressHandler,
            @Value("${workflow.ingress.rocketmq.name-server:}") String nameServer,
            @Value("${workflow.ingress.rocketmq.consumer-group:cpw_doc_ingress_consumer}") String consumerGroup,
            @Value("${workflow.ingress.rocketmq.topic:cpw_doc_ingress}") String topic) {
        this.objectMapper = objectMapper;
        this.ingressHandler = ingressHandler;
        this.topic = topic;
        this.consumer = new DefaultMQPushConsumer(consumerGroup);
        this.consumer.setNamesrvAddr(nameServer);
        this.consumer.setConsumeFromWhere(org.apache.rocketmq.common.consumer.ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (consumer.getNamesrvAddr() == null || consumer.getNamesrvAddr().isBlank()) {
            throw new IllegalStateException("workflow.ingress.rocketmq.name-server must be configured when ingress rocketmq is enabled");
        }
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) this::consumeMessages);
        consumer.start();
        started = true;
        log.info("rocketmq ingress consumer started, topic={}", topic);
    }

    @Override
    public void destroy() {
        if (!started) {
            return;
        }
        try {
            consumer.shutdown();
        } finally {
            started = false;
        }
    }

    private ConsumeConcurrentlyStatus consumeMessages(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
        for (MessageExt message : messages) {
            try {
                DocumentOperationIngressCommand command =
                        objectMapper.readValue(message.getBody(), DocumentOperationIngressCommand.class);
                ingressHandler.handle(command);
            } catch (Exception ex) {
                log.error("failed to consume ingress message, msgId={}, topic={}", message.getMsgId(), message.getTopic(), ex);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}


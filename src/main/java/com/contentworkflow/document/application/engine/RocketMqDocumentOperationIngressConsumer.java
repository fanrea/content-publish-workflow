package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ingress operation consumer: receives commands from RocketMQ and delegates to collaboration engine.
 */
@Component
@ConditionalOnProperty(prefix = "workflow.ingress.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqDocumentOperationIngressConsumer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDocumentOperationIngressConsumer.class);
    private static final String REPAIR_STATUS_PENDING = "PENDING";

    private final DefaultMQPushConsumer consumer;
    private final ObjectMapper objectMapper;
    private final DocumentOperationIngressHandler ingressHandler;
    private final IngressRepairTaskStore ingressRepairTaskStore;
    private final String topic;
    private final int maxReconsumeTimes;
    private volatile boolean started;

    public RocketMqDocumentOperationIngressConsumer(
            ObjectMapper objectMapper,
            DocumentOperationIngressHandler ingressHandler,
            @Value("${workflow.ingress.rocketmq.name-server:}") String nameServer,
            @Value("${workflow.ingress.rocketmq.consumer-group:cpw_doc_ingress_consumer}") String consumerGroup,
            @Value("${workflow.ingress.rocketmq.topic:cpw_doc_ingress}") String topic,
            @Value("${workflow.ingress.rocketmq.max-reconsume-times:16}") int maxReconsumeTimes,
            IngressRepairTaskStore ingressRepairTaskStore) {
        this(
                objectMapper,
                ingressHandler,
                ingressRepairTaskStore,
                topic,
                Math.max(0, maxReconsumeTimes),
                buildConsumer(nameServer, consumerGroup)
        );
    }

    RocketMqDocumentOperationIngressConsumer(
            ObjectMapper objectMapper,
            DocumentOperationIngressHandler ingressHandler,
            IngressRepairTaskStore ingressRepairTaskStore,
            String topic,
            int maxReconsumeTimes,
            DefaultMQPushConsumer consumer) {
        this.objectMapper = objectMapper;
        this.ingressHandler = ingressHandler;
        this.ingressRepairTaskStore = ingressRepairTaskStore;
        this.topic = topic;
        this.maxReconsumeTimes = Math.max(0, maxReconsumeTimes);
        this.consumer = consumer;
    }

    private static DefaultMQPushConsumer buildConsumer(String nameServer, String consumerGroup) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(org.apache.rocketmq.common.consumer.ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        return consumer;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (consumer.getNamesrvAddr() == null || consumer.getNamesrvAddr().isBlank()) {
            throw new IllegalStateException("workflow.ingress.rocketmq.name-server must be configured when ingress rocketmq is enabled");
        }
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerOrderly) this::consumeMessages);
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

    private ConsumeOrderlyStatus consumeMessages(List<MessageExt> messages, ConsumeOrderlyContext context) {
        for (MessageExt message : messages) {
            DocumentOperationIngressCommand command = null;
            try {
                command = objectMapper.readValue(message.getBody(), DocumentOperationIngressCommand.class);
                ingressHandler.handle(command);
            } catch (Exception ex) {
                int retryCount = Math.max(0, message.getReconsumeTimes());
                if (retryCount < maxReconsumeTimes) {
                    log.error("failed to consume ingress message, msgId={}, topic={}, retryCount={}, maxReconsumeTimes={}",
                            message.getMsgId(),
                            message.getTopic(),
                            retryCount,
                            maxReconsumeTimes,
                            ex);
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                }
                try {
                    IngressRepairTask repairTask = new IngressRepairTask(
                            command == null ? null : command.docId(),
                            command == null ? null : command.sessionId(),
                            command == null ? null : command.clientSeq(),
                            new String(message.getBody(), StandardCharsets.UTF_8),
                            ex.getMessage(),
                            retryCount,
                            REPAIR_STATUS_PENDING
                    );
                    ingressRepairTaskStore.saveOrUpdate(repairTask);
                    log.error("ingress message moved to repair task, msgId={}, topic={}, retryCount={}",
                            message.getMsgId(),
                            message.getTopic(),
                            retryCount,
                            ex);
                    continue;
                } catch (Exception persistEx) {
                    log.error("failed to persist ingress repair task, msgId={}, topic={}, retryCount={}",
                            message.getMsgId(),
                            message.getTopic(),
                            retryCount,
                            persistEx);
                }
                return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
            }
        }
        return ConsumeOrderlyStatus.SUCCESS;
    }
}

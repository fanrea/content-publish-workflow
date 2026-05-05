package com.contentworkflow.document.application.gc;

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

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "workflow.gc.compaction.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqDocumentCompactionTaskConsumer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDocumentCompactionTaskConsumer.class);

    private final DefaultMQPushConsumer consumer;
    private final ObjectMapper objectMapper;
    private final DocumentCompactionExecutor compactionExecutor;
    private final String topic;
    private volatile boolean started;

    public RocketMqDocumentCompactionTaskConsumer(
            ObjectMapper objectMapper,
            DocumentCompactionExecutor compactionExecutor,
            @Value("${workflow.gc.compaction.rocketmq.name-server:}") String nameServer,
            @Value("${workflow.gc.compaction.rocketmq.consumer-group:cpw_doc_compaction_task_consumer}") String consumerGroup,
            @Value("${workflow.gc.compaction.rocketmq.topic:cpw_doc_compaction_task}") String topic) {
        this(objectMapper, compactionExecutor, topic, buildConsumer(nameServer, consumerGroup));
    }

    RocketMqDocumentCompactionTaskConsumer(ObjectMapper objectMapper,
                                           DocumentCompactionExecutor compactionExecutor,
                                           String topic,
                                           DefaultMQPushConsumer consumer) {
        this.objectMapper = objectMapper;
        this.compactionExecutor = compactionExecutor;
        this.topic = topic;
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
            throw new IllegalStateException(
                    "workflow.gc.compaction.rocketmq.name-server must be configured when compaction rocketmq is enabled"
            );
        }
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerOrderly) this::consumeMessages);
        consumer.start();
        started = true;
        log.info("rocketmq compaction task consumer started, topic={}", topic);
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
            DocumentCompactionTask task;
            try {
                task = objectMapper.readValue(message.getBody(), DocumentCompactionTask.class);
            } catch (Exception ex) {
                log.warn("invalid compaction task message ignored, msgId={}, topic={}",
                        message.getMsgId(),
                        message.getTopic(),
                        ex);
                continue;
            }
            try {
                compactionExecutor.execute(task);
            } catch (Exception ex) {
                log.error("compaction task execute failed, msgId={}, topic={}, documentId={}, trigger={}, upperClock={}, createdAt={}",
                        message.getMsgId(),
                        message.getTopic(),
                        task.documentId(),
                        task.trigger(),
                        task.segmentUpperClockInclusive(),
                        task.createdAt(),
                        ex);
                return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
            }
        }
        return ConsumeOrderlyStatus.SUCCESS;
    }
}

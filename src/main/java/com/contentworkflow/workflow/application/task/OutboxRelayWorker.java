package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.messaging.WorkflowMessagingProperties;
import com.contentworkflow.common.messaging.outbox.OutboxEventEntity;
import com.contentworkflow.common.messaging.outbox.OutboxEventRepository;
import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 后台工作组件，用于异步执行任务、投递消息或处理补偿逻辑。
 */
@Component
@ConditionalOnProperty(prefix = "workflow.outbox.relay", name = "enabled", havingValue = "true")
public class OutboxRelayWorker {

    private static final Collection<OutboxEventStatus> CLAIMABLE = List.of(OutboxEventStatus.NEW, OutboxEventStatus.FAILED);

    private final OutboxEventRepository repo;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowMessagingProperties props;

    private final String workerId = "outbox-" + UUID.randomUUID();

    @org.springframework.beans.factory.annotation.Value("${workflow.scheduler.local.enabled:true}")
    private boolean localScheduleEnabled;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param repo 参数 repo 对应的业务输入值
     * @param rabbitTemplate 参数 rabbitTemplate 对应的业务输入值
     * @param objectMapper 参数 objectMapper 对应的业务输入值
     * @param props 配置属性对象
     */

    public OutboxRelayWorker(OutboxEventRepository repo,
                             RabbitTemplate rabbitTemplate,
                             ObjectMapper objectMapper,
                             WorkflowMessagingProperties props) {
        this.repo = repo;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    /**
     * 处理 poll once 相关逻辑，并返回对应的执行结果。
     */

    public void pollOnce() {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(1, props.getRelay().getBatchSize());
        int lockSeconds = Math.max(5, props.getRelay().getLockSeconds());
        LocalDateTime lockExpiredBefore = now.minusSeconds(lockSeconds);

        // Do not send MQ messages inside the same DB transaction as outbox state changes.
        List<ClaimedEvent> claimed = claimBatch(now, lockExpiredBefore, batchSize);
        for (ClaimedEvent e : claimed) {
            deliverOne(e, now);
        }
    }

    /**
     * 处理 scheduled poll once 相关逻辑，并返回对应的执行结果。
     */

    @Scheduled(fixedDelayString = "${workflow.outbox.relay.pollDelayMs:1000}")
    public void scheduledPollOnce() {
        if (!localScheduleEnabled) {
            return;
        }
        pollOnce();
    }

    /**
     * 处理 claim batch 相关逻辑，并返回对应的执行结果。
     *
     * @param now 参数 now 对应的业务输入值
     * @param lockExpiredBefore 参数 lockExpiredBefore 对应的业务输入值
     * @param batchSize 参数 batchSize 对应的业务输入值
     * @return 符合条件的结果集合
     */

    @Transactional
    protected List<ClaimedEvent> claimBatch(LocalDateTime now, LocalDateTime lockExpiredBefore, int batchSize) {
        List<OutboxEventEntity> rows = repo.findClaimCandidates(
                CLAIMABLE,
                now,
                lockExpiredBefore,
                PageRequest.of(0, batchSize)
        );
        if (rows.isEmpty()) {
            return List.of();
        }

        // Mark rows as SENDING and acquire the lease before leaving the transaction.
        for (OutboxEventEntity e : rows) {
            e.setStatus(OutboxEventStatus.SENDING);
            e.setLockedBy(workerId);
            e.setLockedAt(now);
        }
        repo.saveAll(rows);

        return rows.stream().map(ClaimedEvent::of).toList();
    }

    /**
     * 处理 deliver one 相关逻辑，并返回对应的执行结果。
     *
     * @param e 参数 e 对应的业务输入值
     * @param now 参数 now 对应的业务输入值
     */

    protected void deliverOne(ClaimedEvent e, LocalDateTime now) {
        try {
            Message msg = toMessage(e);
            rabbitTemplate.send(e.exchangeName(), e.routingKey(), msg);
            markSent(e.id(), now);
        } catch (Exception ex) {
            markFailedOrDead(e.id(), now, ex);
        }
    }

    /**
     * 处理 mark sent 相关逻辑，并返回对应的执行结果。
     *
     * @param id 主键标识
     * @param now 参数 now 对应的业务输入值
     */

    @Transactional
    protected void markSent(Long id, LocalDateTime now) {
        OutboxEventEntity row = repo.findById(id).orElse(null);
        if (row == null) {
            return;
        }
        // Only the worker that currently holds the lease may finalize the event as SENT.
        if (row.getStatus() != OutboxEventStatus.SENDING || !workerId.equals(row.getLockedBy())) {
            return;
        }
        row.setStatus(OutboxEventStatus.SENT);
        row.setSentAt(now);
        row.setNextRetryAt(null);
        row.setErrorMessage(null);
        row.setLockedBy(null);
        row.setLockedAt(null);
        repo.save(row);
    }

    /**
     * 处理 mark failed or dead 相关逻辑，并返回对应的执行结果。
     *
     * @param id 主键标识
     * @param now 参数 now 对应的业务输入值
     * @param ex 异常对象
     */

    @Transactional
    protected void markFailedOrDead(Long id, LocalDateTime now, Exception ex) {
        OutboxEventEntity row = repo.findById(id).orElse(null);
        if (row == null) {
            return;
        }
        if (row.getStatus() != OutboxEventStatus.SENDING || !workerId.equals(row.getLockedBy())) {
            return;
        }

        int attempt = row.getAttempt() + 1;
        row.setAttempt(attempt);

        int maxRetries = Math.max(1, props.getRelay().getMaxRetries());
        int baseDelaySeconds = Math.max(1, props.getRelay().getBaseDelaySeconds());

        if (attempt >= maxRetries) {
            row.setStatus(OutboxEventStatus.DEAD);
            row.setNextRetryAt(null);
        } else {
            long delay = (long) baseDelaySeconds * (1L << Math.min(10, attempt - 1));
            delay = Math.min(delay, 300);
            row.setStatus(OutboxEventStatus.FAILED);
            row.setNextRetryAt(now.plusSeconds(delay));
        }

        row.setErrorMessage(shortError(ex));
        row.setLockedBy(null);
        row.setLockedAt(null);
        repo.save(row);
    }

    /**
     * 处理 to message 相关逻辑，并返回对应的执行结果。
     *
     * @param e 参数 e 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private Message toMessage(ClaimedEvent e) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(e.eventId());
        props.setHeader("x-event-type", e.eventType());
        props.setHeader("x-aggregate-type", e.aggregateType());
        props.setHeader("x-aggregate-id", e.aggregateId());
        if (e.aggregateVersion() != null) {
            props.setHeader("x-aggregate-version", e.aggregateVersion());
        }

        // headersJson is optional; parse failures must not break delivery.
        if (e.headersJson() != null && !e.headersJson().isBlank()) {
            try {
                Map<String, Object> headers = objectMapper.readValue(e.headersJson(), new TypeReference<>() {
                });
                if (headers != null) {
                    for (Map.Entry<String, Object> entry : headers.entrySet()) {
                        if (entry.getKey() != null) {
                            props.setHeader(entry.getKey(), entry.getValue());
                        }
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        byte[] body = (e.payloadJson() == null ? "{}" : e.payloadJson()).getBytes(StandardCharsets.UTF_8);
        return new Message(body, props);
    }

    /**
     * 处理 short error 相关逻辑，并返回对应的执行结果。
     *
     * @param e 参数 e 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private static String shortError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        msg = msg.replace("\r", " ").replace("\n", " ");
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    /**
     * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
     */
    private record ClaimedEvent(
            Long id,
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            Integer aggregateVersion,
            String exchangeName,
            String routingKey,
            String payloadJson,
            String headersJson
    ) {
        /**
         * 处理 of 相关逻辑，并返回对应的执行结果。
         *
         * @param e 参数 e 对应的业务输入值
         * @return 方法处理后的结果对象
         */

        static ClaimedEvent of(OutboxEventEntity e) {
            return new ClaimedEvent(
                    e.getId(),
                    e.getEventId(),
                    e.getEventType(),
                    e.getAggregateType(),
                    e.getAggregateId(),
                    e.getAggregateVersion(),
                    e.getExchangeName(),
                    e.getRoutingKey(),
                    e.getPayloadJson(),
                    e.getHeadersJson()
            );
        }
    }
}

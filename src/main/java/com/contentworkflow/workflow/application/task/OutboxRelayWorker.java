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
 * Outbox relay worker: polls outbox table and sends messages to RabbitMQ.
 *
 * <p>Key idea: business code only writes to outbox; relay is responsible for delivery and retries.</p>
 *
 * <p>Disabled by default. Enable via {@code workflow.outbox.relay.enabled=true}.</p>
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

    public OutboxRelayWorker(OutboxEventRepository repo,
                             RabbitTemplate rabbitTemplate,
                             ObjectMapper objectMapper,
                             WorkflowMessagingProperties props) {
        this.repo = repo;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public void pollOnce() {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(1, props.getRelay().getBatchSize());
        int lockSeconds = Math.max(5, props.getRelay().getLockSeconds());
        LocalDateTime lockExpiredBefore = now.minusSeconds(lockSeconds);

        // 关键点：不要在同一个 DB 事务中直接发 MQ。
        // 否则一旦事务回滚，就会出现“消息已发出但 outbox 状态没更新”的重复投递风险。
        List<ClaimedEvent> claimed = claimBatch(now, lockExpiredBefore, batchSize);
        for (ClaimedEvent e : claimed) {
            deliverOne(e, now);
        }
    }

    @Scheduled(fixedDelayString = "${workflow.outbox.relay.pollDelayMs:1000}")
    public void scheduledPollOnce() {
        if (!localScheduleEnabled) {
            return;
        }
        pollOnce();
    }

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

        // 先把状态置为 SENDING 并落锁，避免多实例重复 claim。
        for (OutboxEventEntity e : rows) {
            e.setStatus(OutboxEventStatus.SENDING);
            e.setLockedBy(workerId);
            e.setLockedAt(now);
        }
        repo.saveAll(rows);

        return rows.stream().map(ClaimedEvent::of).toList();
    }

    protected void deliverOne(ClaimedEvent e, LocalDateTime now) {
        try {
            Message msg = toMessage(e);
            rabbitTemplate.send(e.exchangeName(), e.routingKey(), msg);
            markSent(e.id(), now);
        } catch (Exception ex) {
            markFailedOrDead(e.id(), now, ex);
        }
    }

    @Transactional
    protected void markSent(Long id, LocalDateTime now) {
        OutboxEventEntity row = repo.findById(id).orElse(null);
        if (row == null) {
            return;
        }
        // 防御：只允许“被当前 worker 锁定且处于 SENDING” 的事件进入 SENT。
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

    private static String shortError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        msg = msg.replace("\r", " ").replace("\n", " ");
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    /**
     * 轻量 DTO：避免把 JPA Entity 带出事务范围（减少意外懒加载/脏写风险）。
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

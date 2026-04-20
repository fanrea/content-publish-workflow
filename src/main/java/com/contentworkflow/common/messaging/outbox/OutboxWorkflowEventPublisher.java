package com.contentworkflow.common.messaging.outbox;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.common.messaging.WorkflowMessagingProperties;
import com.contentworkflow.common.messaging.WorkflowMessagingTraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 发布器组件，用于封装事件构建、消息投递或 outbox 发布逻辑。
 */
public class OutboxWorkflowEventPublisher implements WorkflowEventPublisher {

    private final WorkflowMessagingProperties props;
    private final RoutingKeyResolver routingKeyResolver;
    private final ObjectMapper objectMapper;
    private final OutboxEnqueuer enqueuer;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param props 配置属性对象
     * @param routingKeyResolver 参数 routingKeyResolver 对应的业务输入值
     * @param objectMapper 参数 objectMapper 对应的业务输入值
     * @param enqueuer 参数 enqueuer 对应的业务输入值
     */

    public OutboxWorkflowEventPublisher(WorkflowMessagingProperties props,
                                        RoutingKeyResolver routingKeyResolver,
                                        ObjectMapper objectMapper,
                                        OutboxEnqueuer enqueuer) {
        this.props = props;
        this.routingKeyResolver = routingKeyResolver;
        this.objectMapper = objectMapper;
        this.enqueuer = enqueuer;
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @param event 事件对象
     */

    @Override
    public void publish(WorkflowEvent event) {
        Map<String, Object> outboundHeaders = WorkflowMessagingTraceContext.enrichOutboundHeaders(event.headers());
        OutboxEventEntity row = new OutboxEventEntity();
        row.setEventId(event.eventId());
        row.setEventType(event.eventType());
        row.setAggregateType(event.aggregateType());
        row.setAggregateId(event.aggregateId());
        row.setAggregateVersion(event.aggregateVersion());
        row.setExchangeName(props.getExchange());
        row.setRoutingKey(routingKeyResolver.resolve(event.eventType()));
        row.setStatus(OutboxEventStatus.NEW);
        row.setAttempt(0);
        row.setNextRetryAt(null);
        row.setLockedAt(null);
        row.setLockedBy(null);
        row.setErrorMessage(null);
        row.setSentAt(null);
        // createdAt/updatedAt defaults are filled by the repository before insert.

        row.setPayloadJson(toJsonQuietly(event.payload()));
        row.setHeadersJson(toJsonQuietly(outboundHeaders));
        row.setTraceId(headerValue(outboundHeaders, WorkflowLogContext.TRACE_ID_HEADER, WorkflowLogContext.TRACE_ID_KEY));
        row.setRequestId(headerValue(outboundHeaders, WorkflowLogContext.REQUEST_ID_HEADER, WorkflowLogContext.REQUEST_ID_KEY));

        enqueuer.enqueue(row);
    }

    private String headerValue(Map<String, Object> headers, String... keys) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = headers.get(key);
            if (value == null) {
                continue;
            }
            String normalized = String.valueOf(value).trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return null;
    }

    /**
     * 处理 to json quietly 相关逻辑，并返回对应的执行结果。
     *
     * @param obj 参数 obj 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private String toJsonQuietly(Object obj) {
        if (obj == null) {
            return "{}";
        }
        if (obj instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            // Do not break the main flow due to serialization errors. Store minimal info for debugging.
            return "{\"_serializeError\":true,\"_type\":\"" + obj.getClass().getName() + "\"}";
        }
    }
}

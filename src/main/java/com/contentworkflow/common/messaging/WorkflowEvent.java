package com.contentworkflow.common.messaging;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
 */
public record WorkflowEvent(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Integer aggregateVersion,
        Object payload,
        Map<String, Object> headers,
        LocalDateTime occurredAt
) {

    /**
     * 处理 of 相关逻辑，并返回对应的执行结果。
     *
     * @param eventType 参数 eventType 对应的业务输入值
     * @param aggregateType 参数 aggregateType 对应的业务输入值
     * @param aggregateId 相关业务对象的唯一标识
     * @param aggregateVersion 参数 aggregateVersion 对应的业务输入值
     * @param payload 参数 payload 对应的业务输入值
     * @param headers 参数 headers 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static WorkflowEvent of(String eventType,
                                   String aggregateType,
                                   String aggregateId,
                                   Integer aggregateVersion,
                                   Object payload,
                                   Map<String, Object> headers) {
        return new WorkflowEvent(
                UUID.randomUUID().toString(),
                Objects.requireNonNull(eventType, "eventType"),
                Objects.requireNonNull(aggregateType, "aggregateType"),
                Objects.requireNonNull(aggregateId, "aggregateId"),
                aggregateVersion,
                payload,
                headers,
                LocalDateTime.now()
        );
    }
}


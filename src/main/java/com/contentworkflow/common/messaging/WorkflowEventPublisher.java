package com.contentworkflow.common.messaging;

/**
 * 领域事件发布器（扩展点）。
 *
 * <p>本项目采用 outbox 模式做“可靠投递”：业务侧只负责把事件写入 outbox（同事务落库），
 * 再由 outbox relay 异步扫描 outbox 并投递到 RabbitMQ，从而避免 DB 与 MQ 的不一致。</p>
 *
 * <p>注意：publisher 只负责“提交事件”，不保证实时送达；送达由 outbox relay 闭环负责。</p>
 */
public interface WorkflowEventPublisher {

    void publish(WorkflowEvent event);
}


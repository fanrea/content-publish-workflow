package com.contentworkflow.common.messaging.outbox;

/**
 * Outbox 入队（写库）扩展点。
 *
 * <p>业务侧应在同一事务内调用该接口，把“需要对外传播的事件”写入 outbox。</p>
 */
public interface OutboxEnqueuer {

    void enqueue(OutboxEventEntity event);
}


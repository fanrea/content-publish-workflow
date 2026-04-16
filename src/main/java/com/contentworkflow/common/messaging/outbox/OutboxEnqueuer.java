package com.contentworkflow.common.messaging.outbox;

/**
 * 接口契约，定义当前模块对外暴露的能力边界和协作方式。
 */
public interface OutboxEnqueuer {

    /**
     * 处理 enqueue 相关逻辑，并返回对应的执行结果。
     *
     * @param event 事件对象
     */

    void enqueue(OutboxEventEntity event);
}


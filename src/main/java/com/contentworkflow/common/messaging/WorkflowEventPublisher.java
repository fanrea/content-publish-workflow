package com.contentworkflow.common.messaging;

/**
 * 发布器组件，用于封装事件构建、消息投递或 outbox 发布逻辑。
 */
public interface WorkflowEventPublisher {

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @param event 事件对象
     */

    void publish(WorkflowEvent event);
}


package com.contentworkflow.common.messaging.outbox;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventPublisher;

/**
 * 发布器组件，用于封装事件构建、消息投递或 outbox 发布逻辑。
 */
public class NoopWorkflowEventPublisher implements WorkflowEventPublisher {
    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @param event 事件对象
     */

    @Override
    public void publish(WorkflowEvent event) {
        // Intentionally no-op.
    }
}


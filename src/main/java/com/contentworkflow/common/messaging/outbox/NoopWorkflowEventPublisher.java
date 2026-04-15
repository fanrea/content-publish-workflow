package com.contentworkflow.common.messaging.outbox;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventPublisher;

/**
 * 默认 no-op publisher：不落库、不发消息。
 *
 * <p>用于保证业务代码可以安全注入 publisher，即使没有开启 outbox/mq。</p>
 */
public class NoopWorkflowEventPublisher implements WorkflowEventPublisher {
    @Override
    public void publish(WorkflowEvent event) {
        // Intentionally no-op.
    }
}


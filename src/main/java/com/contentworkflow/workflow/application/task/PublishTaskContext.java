package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;

/**
 * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
 */
public record PublishTaskContext(
        ContentDraft draft,
        ContentSnapshot snapshot,
        PublishTask task,
        String operator,
        /**
         * Event publishing extension point.
         *
         * <p>Handlers can publish best-effort domain events (usually into outbox), and let a relay deliver them to MQ.
         * Default implementation is no-op so it won't affect existing main flows.</p>
         */
        WorkflowEventPublisher eventPublisher
) {
}

package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;

/**
 * Execution context for a publish task.
 *
 * <p>To keep the worker testable, all required data is passed in explicitly.</p>
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

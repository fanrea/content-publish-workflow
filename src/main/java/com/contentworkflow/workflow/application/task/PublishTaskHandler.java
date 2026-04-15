package com.contentworkflow.workflow.application.task;

import com.contentworkflow.workflow.domain.enums.PublishTaskType;

/**
 * Publish task handler (pluggable).
 *
 * <p>Each {@code taskType} maps to one handler responsible for publish side effects, such as
 * refreshing search index, syncing downstream read models, or sending notifications.</p>
 *
 * <p>Compensation semantics: when publish fails or a rollback happens, {@link #compensate} may be
 * invoked as a best-effort attempt to undo or fix side effects.</p>
 */
public interface PublishTaskHandler {

    PublishTaskType taskType();

    void execute(PublishTaskContext ctx) throws Exception;

    default void compensate(PublishTaskContext ctx) throws Exception {
        // No-op by default.
    }
}

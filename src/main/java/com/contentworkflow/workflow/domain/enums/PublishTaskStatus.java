package com.contentworkflow.workflow.domain.enums;

/**
 * Publish task lifecycle states.
 */
public enum PublishTaskStatus {
    /**
     * Task has been created and is waiting to be claimed by a worker.
     */
    PENDING,
    /**
     * Task is currently being handled by a worker.
     */
    RUNNING,
    /**
     * The request has been dispatched to a downstream integration and is waiting
     * for explicit confirmation from the consumer side.
     */
    AWAITING_CONFIRMATION,
    /**
     * Task completed successfully.
     */
    SUCCESS,
    /**
     * Task failed but can be retried.
     */
    FAILED,
    /**
     * Task exhausted automatic retries and needs intervention.
     */
    DEAD
}

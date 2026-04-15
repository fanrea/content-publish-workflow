package com.contentworkflow.common.messaging;

/**
 * Workflow event type constants.
 *
 * <p>Task events represent side effects accepted by this service and relayed through outbox +
 * RabbitMQ. Publish state events represent workflow milestones after orchestration.</p>
 */
public final class WorkflowEventTypes {

    private WorkflowEventTypes() {
    }

    public static final String SEARCH_INDEX_REFRESH_REQUESTED = "SEARCH_INDEX_REFRESH_REQUESTED";
    public static final String DOWNSTREAM_READ_MODEL_SYNC_REQUESTED = "DOWNSTREAM_READ_MODEL_SYNC_REQUESTED";
    public static final String PUBLISH_NOTIFICATION_REQUESTED = "PUBLISH_NOTIFICATION_REQUESTED";

    public static final String CONTENT_PUBLISHED = "CONTENT_PUBLISHED";
    public static final String CONTENT_PUBLISH_FAILED = "CONTENT_PUBLISH_FAILED";
}

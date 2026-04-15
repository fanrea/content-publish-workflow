package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventTypes;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Build payloads for publish side-effect events.
 */
public final class PublishTaskEventFactory {

    private PublishTaskEventFactory() {
    }

    public static WorkflowEvent buildSearchIndexRefreshRequestedEvent(PublishTaskContext ctx) {
        return buildEvent(
                WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED,
                ctx,
                new SearchIndexRefreshRequestedPayload(
                        ctx.task().getId(),
                        ctx.draft().getId(),
                        ctx.draft().getBizNo(),
                        ctx.snapshot().getId(),
                        ctx.snapshot().getPublishedVersion(),
                        ctx.snapshot().getSourceDraftVersion(),
                        ctx.snapshot().getTitle(),
                        ctx.snapshot().getSummary(),
                        ctx.snapshot().getBody(),
                        ctx.snapshot().isRollback(),
                        normalizeOperator(ctx.operator(), ctx.snapshot().getOperator()),
                        ctx.snapshot().getPublishedAt()
                )
        );
    }

    public static WorkflowEvent buildReadModelSyncRequestedEvent(PublishTaskContext ctx) {
        return buildEvent(
                WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED,
                ctx,
                new ReadModelSyncRequestedPayload(
                        ctx.task().getId(),
                        ctx.draft().getId(),
                        ctx.draft().getBizNo(),
                        ctx.snapshot().getId(),
                        ctx.snapshot().getPublishedVersion(),
                        ctx.snapshot().getSourceDraftVersion(),
                        ctx.snapshot().getTitle(),
                        ctx.snapshot().getSummary(),
                        ctx.snapshot().getBody(),
                        ctx.snapshot().isRollback(),
                        normalizeOperator(ctx.operator(), ctx.snapshot().getOperator()),
                        ctx.snapshot().getPublishedAt()
                )
        );
    }

    public static WorkflowEvent buildPublishNotificationRequestedEvent(PublishTaskContext ctx) {
        return buildEvent(
                WorkflowEventTypes.PUBLISH_NOTIFICATION_REQUESTED,
                ctx,
                new PublishNotificationRequestedPayload(
                        ctx.task().getId(),
                        ctx.draft().getId(),
                        ctx.draft().getBizNo(),
                        ctx.snapshot().getId(),
                        ctx.snapshot().getPublishedVersion(),
                        ctx.snapshot().getTitle(),
                        ctx.snapshot().getSummary(),
                        ctx.snapshot().isRollback(),
                        normalizeOperator(ctx.operator(), ctx.snapshot().getOperator()),
                        ctx.snapshot().getPublishedAt()
                )
        );
    }

    private static WorkflowEvent buildEvent(String eventType, PublishTaskContext ctx, Object payload) {
        PublishTask task = ctx.task();
        ContentDraft draft = ctx.draft();
        ContentSnapshot snapshot = ctx.snapshot();
        return WorkflowEvent.of(
                eventType,
                "content_publish_task",
                String.valueOf(task.getId()),
                snapshot.getPublishedVersion(),
                payload,
                buildHeaders(draft, snapshot, task, ctx.operator())
        );
    }

    private static Map<String, Object> buildHeaders(ContentDraft draft,
                                                    ContentSnapshot snapshot,
                                                    PublishTask task,
                                                    String operator) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("taskType", task.getTaskType().name());
        headers.put("draftId", draft.getId());
        headers.put("bizNo", draft.getBizNo());
        headers.put("publishedVersion", snapshot.getPublishedVersion());
        headers.put("snapshotId", snapshot.getId());
        headers.put("rollback", snapshot.isRollback());
        headers.put("operator", normalizeOperator(operator, snapshot.getOperator()));
        return headers;
    }

    private static String normalizeOperator(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "system";
    }

    public record SearchIndexRefreshRequestedPayload(
            Long taskId,
            Long draftId,
            String bizNo,
            Long snapshotId,
            Integer publishedVersion,
            Integer sourceDraftVersion,
            String title,
            String summary,
            String body,
            boolean rollback,
            String operator,
            LocalDateTime publishedAt
    ) {
    }

    public record ReadModelSyncRequestedPayload(
            Long taskId,
            Long draftId,
            String bizNo,
            Long snapshotId,
            Integer publishedVersion,
            Integer sourceDraftVersion,
            String title,
            String summary,
            String body,
            boolean rollback,
            String operator,
            LocalDateTime publishedAt
    ) {
    }

    public record PublishNotificationRequestedPayload(
            Long taskId,
            Long draftId,
            String bizNo,
            Long snapshotId,
            Integer publishedVersion,
            String title,
            String summary,
            boolean rollback,
            String operator,
            LocalDateTime publishedAt
    ) {
    }
}

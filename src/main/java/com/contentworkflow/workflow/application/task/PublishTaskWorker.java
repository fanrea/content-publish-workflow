package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.common.messaging.WorkflowEventTypes;
import com.contentworkflow.workflow.application.store.PublishLogEntry;
import com.contentworkflow.workflow.application.store.WorkflowAuditLogFactory;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Publish task worker: claims PENDING/FAILED tasks, executes side effects, retries on failure,
 * and may trigger best-effort compensation while advancing the draft status.
 *
 * <p>The goal is to decouple the publish API from synchronous side effects, so the publish flow
 * becomes retryable, compensatable, and observable.</p>
 */
@Component
public class PublishTaskWorker {

    private final WorkflowStore store;
    private final Map<PublishTaskType, PublishTaskHandler> handlers;
    private final WorkflowEventPublisher eventPublisher;

    private final String workerId = "cpw-" + UUID.randomUUID();

    @Value("${workflow.task.worker.enabled:true}")
    private boolean enabled;

    @Value("${workflow.task.worker.batchSize:20}")
    private int batchSize;

    @Value("${workflow.task.worker.lockSeconds:60}")
    private int lockSeconds;

    @Value("${workflow.task.retry.maxRetries:5}")
    private int maxRetries;

    @Value("${workflow.task.retry.baseDelaySeconds:5}")
    private int baseDelaySeconds;

    @Value("${workflow.scheduler.local.enabled:true}")
    private boolean localScheduleEnabled;

    public PublishTaskWorker(WorkflowStore store,
                             List<PublishTaskHandler> handlerList,
                             WorkflowEventPublisher eventPublisher) {
        this.store = store;
        this.eventPublisher = eventPublisher;
        EnumMap<PublishTaskType, PublishTaskHandler> map = new EnumMap<>(PublishTaskType.class);
        for (PublishTaskHandler h : handlerList) {
            map.put(h.taskType(), h);
        }
        this.handlers = Map.copyOf(map);
    }

    @Transactional
    public void pollOnce() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PublishTask> claimed = store.claimRunnablePublishTasks(batchSize, workerId, now, lockSeconds);
        for (PublishTask task : claimed) {
            executeOne(task, now);
        }
    }

    /**
     * Local fallback scheduler for development / standalone mode.
     */
    @Scheduled(fixedDelayString = "${workflow.task.worker.pollDelayMs:1000}")
    public void scheduledPollOnce() {
        if (!localScheduleEnabled) {
            return;
        }
        pollOnce();
    }

    private void executeOne(PublishTask task, LocalDateTime now) {
        // Double-check status to avoid duplicate execution.
        if (task.getStatus() != PublishTaskStatus.RUNNING) {
            return;
        }

        ContentDraft draft = store.findDraftById(task.getDraftId()).orElse(null);
        if (draft == null) {
            markDead(task, now, "draft not found");
            return;
        }

        ContentSnapshot snapshot = store.listSnapshots(task.getDraftId()).stream()
                .filter(s -> Objects.equals(s.getPublishedVersion(), task.getPublishedVersion()))
                .findFirst()
                .orElse(null);
        if (snapshot == null) {
            markDead(task, now, "snapshot not found for version=" + task.getPublishedVersion());
            return;
        }

        PublishTaskHandler handler = handlers.get(task.getTaskType());
        if (handler == null) {
            markDead(task, now, "handler not found for type=" + task.getTaskType());
            return;
        }

        PublishTaskContext ctx = new PublishTaskContext(draft, snapshot, task, snapshot.getOperator(), eventPublisher);

        try {
            handler.execute(ctx);
            markSuccess(task, now);
            tryFinalizeDraft(draft.getId(), task.getPublishedVersion(), now, snapshot.getOperator());
        } catch (Exception e) {
            markFailedOrDead(task, now, e);
            if (task.getStatus() == PublishTaskStatus.DEAD) {
                markDraftFailedAndCompensate(draft.getId(), task.getPublishedVersion(), now, snapshot.getOperator(), e.getMessage());
            }
        }
    }

    private void markSuccess(PublishTask task, LocalDateTime now) {
        String operatorName = workerId;
        task.setStatus(PublishTaskStatus.SUCCESS);
        task.setErrorMessage(null);
        task.setNextRunAt(null);
        task.setLockedBy(null);
        task.setLockedAt(null);
        task.setUpdatedAt(now);
        store.updatePublishTask(task);

        store.insertPublishLog(WorkflowAuditLogFactory.taskAction(task, "TASK_SUCCESS", workerId, operatorName)
                .beforeStatus(PublishTaskStatus.RUNNING.name())
                .afterStatus(PublishTaskStatus.SUCCESS.name())
                .result(WorkflowAuditResult.SUCCESS)
                .remark(task.getTaskType() + "@" + task.getPublishedVersion())
                .createdAt(now)
                .build());
    }

    private void markFailedOrDead(PublishTask task, LocalDateTime now, Exception e) {
        int retry = (task.getRetryTimes() == null ? 0 : task.getRetryTimes()) + 1;
        task.setRetryTimes(retry);
        task.setErrorMessage(shortError(e));
        task.setLockedBy(null);
        task.setLockedAt(null);
        task.setUpdatedAt(now);

        if (retry >= Math.max(1, maxRetries)) {
            task.setStatus(PublishTaskStatus.DEAD);
            task.setNextRunAt(null);
            store.updatePublishTask(task);
            store.insertPublishLog(WorkflowAuditLogFactory.taskAction(task, "TASK_DEAD", workerId, workerId)
                    .beforeStatus(PublishTaskStatus.RUNNING.name())
                    .afterStatus(PublishTaskStatus.DEAD.name())
                    .result(WorkflowAuditResult.INTERVENTION_REQUIRED)
                    .errorCode("TASK_RETRY_EXHAUSTED")
                    .errorMessage(task.getErrorMessage())
                    .remark(task.getTaskType() + "@" + task.getPublishedVersion() + " err=" + task.getErrorMessage())
                    .createdAt(now)
                    .build());
            return;
        }

        // Exponential backoff: baseDelaySeconds * 2^(retry-1), capped at 5 minutes.
        long delay = (long) baseDelaySeconds * (1L << Math.min(10, retry - 1));
        delay = Math.min(delay, 300);
        task.setStatus(PublishTaskStatus.FAILED);
        task.setNextRunAt(now.plusSeconds(delay));
        store.updatePublishTask(task);
        store.insertPublishLog(WorkflowAuditLogFactory.taskAction(task, "TASK_FAILED", workerId, workerId)
                .beforeStatus(PublishTaskStatus.RUNNING.name())
                .afterStatus(PublishTaskStatus.FAILED.name())
                .result(WorkflowAuditResult.RETRYING)
                .errorCode("TASK_EXECUTION_FAILED")
                .errorMessage(task.getErrorMessage())
                .remark(task.getTaskType() + "@" + task.getPublishedVersion() + " retry=" + retry + " nextRunIn=" + delay + "s")
                .createdAt(now)
                .build());
    }

    private void markDead(PublishTask task, LocalDateTime now, String reason) {
        task.setStatus(PublishTaskStatus.DEAD);
        task.setErrorMessage(reason);
        task.setNextRunAt(null);
        task.setLockedBy(null);
        task.setLockedAt(null);
        task.setUpdatedAt(now);
        store.updatePublishTask(task);
    }

    private void tryFinalizeDraft(Long draftId, Integer publishedVersion, LocalDateTime now, String operator) {
        ContentDraft draft = store.findDraftById(draftId).orElse(null);
        if (draft == null) {
            return;
        }
        // Only finalize the current version; ignore stale tasks.
        if (!Objects.equals(draft.getPublishedVersion(), publishedVersion)) {
            return;
        }
        if (draft.getStatus() != WorkflowStatus.PUBLISHING) {
            return;
        }

        boolean hasDead = store.listPublishTasks(draftId).stream()
                .filter(t -> Objects.equals(t.getPublishedVersion(), publishedVersion))
                .anyMatch(t -> t.getStatus() == PublishTaskStatus.DEAD);
        if (hasDead) {
            return;
        }

        boolean allSuccess = store.listPublishTasks(draftId).stream()
                .filter(t -> Objects.equals(t.getPublishedVersion(), publishedVersion))
                .allMatch(t -> t.getStatus() == PublishTaskStatus.SUCCESS);
        if (!allSuccess) {
            return;
        }

        draft.setStatus(WorkflowStatus.PUBLISHED);
        draft.setUpdatedAt(now);
        store.updateDraft(draft);
        store.insertPublishLog(WorkflowAuditLogFactory.publishSystemAction(draftId, publishedVersion, "PUBLISH_COMPLETED", workerId, operator == null ? "system" : operator)
                .beforeStatus(WorkflowStatus.PUBLISHING.name())
                .afterStatus(WorkflowStatus.PUBLISHED.name())
                .result(WorkflowAuditResult.SUCCESS)
                .remark("version=" + publishedVersion)
                .createdAt(now)
                .build());

        // Extension point: emit a domain event after publish completed (outbox -> relay -> RabbitMQ).
        // Best-effort: publisher defaults to no-op, so it won't break the main flow.
        eventPublisher.publish(WorkflowEvent.of(
                WorkflowEventTypes.CONTENT_PUBLISHED,
                "content_draft",
                String.valueOf(draftId),
                publishedVersion,
                Map.of("draftId", draftId, "publishedVersion", publishedVersion),
                Map.of("operator", operator == null ? "system" : operator)
        ));
    }

    private void markDraftFailedAndCompensate(Long draftId,
                                              Integer publishedVersion,
                                              LocalDateTime now,
                                              String operator,
                                              String reason) {
        ContentDraft draft = store.findDraftById(draftId).orElse(null);
        if (draft == null) {
            return;
        }
        if (!Objects.equals(draft.getPublishedVersion(), publishedVersion)) {
            return;
        }

        draft.setStatus(WorkflowStatus.PUBLISH_FAILED);
        draft.setUpdatedAt(now);
        store.updateDraft(draft);
        store.insertPublishLog(WorkflowAuditLogFactory.publishSystemAction(draftId, publishedVersion, "PUBLISH_FAILED", workerId, operator == null ? "system" : operator)
                .beforeStatus(WorkflowStatus.PUBLISHING.name())
                .afterStatus(WorkflowStatus.PUBLISH_FAILED.name())
                .result(WorkflowAuditResult.FAILED)
                .errorCode("TASK_COMPENSATION_REQUIRED")
                .errorMessage(reason)
                .remark(reason)
                .createdAt(now)
                .build());

        // Extension point: publish-failed event for downstream alerting/rollback/manual intervention.
        eventPublisher.publish(WorkflowEvent.of(
                WorkflowEventTypes.CONTENT_PUBLISH_FAILED,
                "content_draft",
                String.valueOf(draftId),
                publishedVersion,
                Map.of("draftId", draftId, "publishedVersion", publishedVersion, "reason", reason),
                Map.of("operator", operator == null ? "system" : operator)
        ));

        // Best-effort compensation for tasks already succeeded in this version (handlers are no-op by default).
        List<PublishTask> succeeded = store.listPublishTasks(draftId).stream()
                .filter(t -> Objects.equals(t.getPublishedVersion(), publishedVersion))
                .filter(t -> t.getStatus() == PublishTaskStatus.SUCCESS)
                .toList();

        ContentSnapshot snapshot = store.listSnapshots(draftId).stream()
                .filter(s -> Objects.equals(s.getPublishedVersion(), publishedVersion))
                .findFirst()
                .orElse(null);
        if (snapshot == null) {
            return;
        }
        for (PublishTask t : succeeded) {
            PublishTaskHandler handler = handlers.get(t.getTaskType());
            if (handler == null) {
                continue;
            }
            try {
                handler.compensate(new PublishTaskContext(draft, snapshot, t, operator, eventPublisher));
                store.insertPublishLog(WorkflowAuditLogFactory.taskAction(t, "TASK_COMPENSATED", workerId, workerId)
                        .beforeStatus(PublishTaskStatus.SUCCESS.name())
                        .afterStatus(PublishTaskStatus.SUCCESS.name())
                        .result(WorkflowAuditResult.SUCCESS)
                        .remark(t.getTaskType() + "@" + publishedVersion)
                        .createdAt(now)
                        .build());
            } catch (Exception ignored) {
                // Compensation failure must not block the main flow. In production, record it or enqueue a retry task.
            }
        }
    }

    private static String shortError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        // Avoid storing very long stack traces in the error field.
        msg = msg.replace("\r", " ").replace("\n", " ");
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}

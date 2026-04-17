package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.common.messaging.WorkflowEventTypes;
import com.contentworkflow.workflow.application.store.WorkflowAuditLogFactory;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coordinates publish-task progress from dispatch to downstream confirmation.
 */
@Service
public class PublishTaskProgressService {

    private final WorkflowStore store;
    private final WorkflowEventPublisher eventPublisher;

    @Value("${workflow.draft.operationLockSeconds:1800}")
    private int draftOperationLockSeconds;

    public PublishTaskProgressService(WorkflowStore store, WorkflowEventPublisher eventPublisher) {
        this.store = store;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PublishTask markTaskDispatched(PublishTask task, LocalDateTime now, String dispatcher) {
        PublishTask persisted = reloadTask(task.getDraftId(), task.getId());
        PublishTaskStatus beforeStatus = persisted.getStatus();
        persisted.setStatus(PublishTaskStatus.AWAITING_CONFIRMATION);
        persisted.setErrorMessage(null);
        persisted.setNextRunAt(null);
        persisted.setLockedBy(null);
        persisted.setLockedAt(null);
        persisted.setUpdatedAt(now);
        persisted = store.updatePublishTask(persisted);
        renewDraftOperationLease(persisted.getDraftId(), persisted.getPublishedVersion(), dispatcher, now);

        store.insertPublishLog(WorkflowAuditLogFactory.taskAction(persisted, "TASK_DISPATCHED", dispatcher, dispatcher)
                .beforeStatus(beforeStatus.name())
                .afterStatus(PublishTaskStatus.AWAITING_CONFIRMATION.name())
                .result(WorkflowAuditResult.ACCEPTED)
                .remark(persisted.getTaskType() + "@" + persisted.getPublishedVersion())
                .createdAt(now)
                .build());
        return persisted;
    }

    @Transactional
    public PublishTask confirmTaskSuccess(Long draftId,
                                          Long taskId,
                                          Integer publishedVersion,
                                          PublishTaskType taskType,
                                          String confirmer,
                                          String actionType,
                                          String remark,
                                          LocalDateTime now) {
        PublishTask task = findTask(draftId, taskId);
        if (task == null) {
            return null;
        }
        if (!Objects.equals(task.getPublishedVersion(), publishedVersion) || task.getTaskType() != taskType) {
            return task;
        }
        if (task.getStatus() == PublishTaskStatus.SUCCESS) {
            tryFinalizeDraft(draftId, publishedVersion, now, confirmer);
            return task;
        }
        if (task.getStatus() != PublishTaskStatus.AWAITING_CONFIRMATION
                && task.getStatus() != PublishTaskStatus.RUNNING) {
            return task;
        }

        PublishTaskStatus beforeStatus = task.getStatus();
        task.setStatus(PublishTaskStatus.SUCCESS);
        task.setErrorMessage(null);
        task.setNextRunAt(null);
        task.setLockedBy(null);
        task.setLockedAt(null);
        task.setUpdatedAt(now);
        task = store.updatePublishTask(task);
        renewDraftOperationLease(draftId, publishedVersion, confirmer, now);

        store.insertPublishLog(WorkflowAuditLogFactory.taskAction(task, actionType, "mq-consumer", confirmer)
                .beforeStatus(beforeStatus.name())
                .afterStatus(PublishTaskStatus.SUCCESS.name())
                .result(WorkflowAuditResult.SUCCESS)
                .remark(remark)
                .createdAt(now)
                .build());

        tryFinalizeDraft(draftId, publishedVersion, now, confirmer);
        return task;
    }

    @Transactional
    public void renewLeasesForAwaitingTasks(LocalDateTime now, String renewer) {
        Set<String> uniqueTargets = store.listPublishTasksByStatus(PublishTaskStatus.AWAITING_CONFIRMATION).stream()
                .map(task -> task.getDraftId() + ":" + task.getPublishedVersion())
                .collect(Collectors.toSet());
        for (String key : uniqueTargets) {
            String[] parts = key.split(":");
            Long draftId = Long.valueOf(parts[0]);
            Integer publishedVersion = Integer.valueOf(parts[1]);
            ContentDraft draft = store.findDraftById(draftId).orElse(null);
            if (draft == null) {
                continue;
            }
            if (!Objects.equals(draft.getPublishedVersion(), publishedVersion)) {
                continue;
            }
            if (draft.getStatus() != WorkflowStatus.PUBLISHING && draft.getStatus() != WorkflowStatus.PUBLISH_FAILED) {
                continue;
            }
            renewDraftOperationLease(draftId, publishedVersion, renewer, now);
        }
    }

    private PublishTask findTask(Long draftId, Long taskId) {
        return store.listPublishTasks(draftId).stream()
                .filter(item -> Objects.equals(item.getId(), taskId))
                .findFirst()
                .orElse(null);
    }

    private PublishTask reloadTask(Long draftId, Long taskId) {
        PublishTask task = findTask(draftId, taskId);
        if (task == null) {
            throw new IllegalStateException("task not found: " + taskId);
        }
        return task;
    }

    private void renewDraftOperationLease(Long draftId, Integer publishedVersion, String renewer, LocalDateTime now) {
        store.renewDraftOperationLock(
                draftId,
                publishedVersion,
                renewer,
                now,
                now.plusSeconds(Math.max(60, draftOperationLockSeconds))
        );
    }

    private void tryFinalizeDraft(Long draftId, Integer publishedVersion, LocalDateTime now, String operator) {
        ContentDraft draft = store.findDraftById(draftId).orElse(null);
        if (draft == null) {
            return;
        }
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
        try {
            draft = store.updateDraft(draft, EnumSet.of(WorkflowStatus.PUBLISHING));
        } catch (BusinessException ex) {
            if (isConditionalTransitionConflict(ex)) {
                return;
            }
            throw ex;
        }
        store.releaseDraftOperationLock(draftId, publishedVersion);
        store.insertPublishLog(WorkflowAuditLogFactory.publishSystemAction(draftId, publishedVersion, "PUBLISH_COMPLETED", "mq-consumer", operator == null ? "system" : operator)
                .beforeStatus(WorkflowStatus.PUBLISHING.name())
                .afterStatus(WorkflowStatus.PUBLISHED.name())
                .result(WorkflowAuditResult.SUCCESS)
                .remark("version=" + publishedVersion)
                .createdAt(now)
                .build());
        eventPublisher.publish(WorkflowEvent.of(
                WorkflowEventTypes.CONTENT_PUBLISHED,
                "content_draft",
                String.valueOf(draftId),
                publishedVersion,
                Map.of("draftId", draftId, "publishedVersion", publishedVersion),
                Map.of("operator", operator == null ? "system" : operator)
        ));
    }

    private boolean isConditionalTransitionConflict(BusinessException ex) {
        return "CONCURRENT_MODIFICATION".equals(ex.getCode())
                || "INVALID_WORKFLOW_STATE".equals(ex.getCode());
    }
}

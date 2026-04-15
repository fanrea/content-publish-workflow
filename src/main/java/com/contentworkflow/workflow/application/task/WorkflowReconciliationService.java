package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.cache.CacheNames;
import com.contentworkflow.common.messaging.outbox.OutboxEventEntity;
import com.contentworkflow.common.messaging.outbox.OutboxEventRepository;
import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;
import com.contentworkflow.workflow.application.store.PublishLogEntry;
import com.contentworkflow.workflow.application.store.WorkflowAuditLogFactory;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reconciliation scans triggered by XXL-Job.
 *
 * <p>These scans do not replace worker retry logic. They are the operational safety net for items
 * that already reached DEAD state and now require manual intervention or follow-up compensation.</p>
 */
@Service
public class WorkflowReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowReconciliationService.class);
    private static final String TASK_INTERVENTION_ACTION = "TASK_MANUAL_INTERVENTION_REQUIRED";
    private static final String OUTBOX_INTERVENTION_ACTION = "OUTBOX_MANUAL_INTERVENTION_REQUIRED";

    private final WorkflowStore workflowStore;
    private final OutboxEventRepository outboxEventRepository;
    private final CacheManager cacheManager;

    public WorkflowReconciliationService(WorkflowStore workflowStore,
                                         OutboxEventRepository outboxEventRepository,
                                         CacheManager cacheManager) {
        this.workflowStore = workflowStore;
        this.outboxEventRepository = outboxEventRepository;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public int scanDeadPublishTasks(int limit) {
        int safeLimit = Math.max(1, limit);
        List<PublishTask> deadTasks = workflowStore.listPublishTasksByStatus(PublishTaskStatus.DEAD).stream()
                .limit(safeLimit)
                .toList();

        int created = 0;
        for (PublishTask task : deadTasks) {
            ContentDraft draft = workflowStore.findDraftById(task.getDraftId()).orElse(null);
            if (draft == null) {
                continue;
            }
            String marker = buildTaskMarker(task);
            boolean alreadyLogged = workflowStore.listPublishLogs(task.getDraftId()).stream()
                    .anyMatch(logEntry -> TASK_INTERVENTION_ACTION.equals(logEntry.getActionType())
                            && marker.equals(logEntry.getRemark()));
            if (alreadyLogged) {
                continue;
            }

            workflowStore.insertPublishLog(WorkflowAuditLogFactory.taskAction(task, TASK_INTERVENTION_ACTION, "xxl-job", "xxl-job")
                    .beforeStatus(task.getStatus().name())
                    .afterStatus(task.getStatus().name())
                    .result(WorkflowAuditResult.INTERVENTION_REQUIRED)
                    .errorCode("TASK_DEAD_SCAN")
                    .errorMessage(task.getErrorMessage())
                    .remark(marker)
                    .createdAt(LocalDateTime.now())
                    .build());
            created++;
        }

        if (created > 0) {
            log.warn("workflow dead publish task scan created intervention logs count={}", created);
        } else {
            log.info("workflow dead publish task scan finished without new intervention logs");
        }
        return created;
    }

    @Transactional
    public int scanDeadOutboxEvents(int limit) {
        int safeLimit = Math.max(1, limit);
        List<OutboxEventEntity> deadEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.DEAD,
                PageRequest.of(0, safeLimit)
        );

        Cache markerCache = cacheManager.getCache(CacheNames.DEAD_OUTBOX_SCAN_MARKER);
        int discovered = 0;
        for (OutboxEventEntity event : deadEvents) {
            String markerKey = event.getEventId() == null ? "outbox:" + event.getId() : event.getEventId();
            if (markerCache != null && markerCache.putIfAbsent(markerKey, LocalDateTime.now()) != null) {
                continue;
            }

            log.warn("dead outbox event requires intervention eventId={} aggregateType={} aggregateId={} eventType={} error={}",
                    event.getEventId(), event.getAggregateType(), event.getAggregateId(), event.getEventType(), event.getErrorMessage());
            tryAppendDraftAuditForOutboxEvent(event);
            discovered++;
        }

        if (discovered == 0) {
            log.info("workflow dead outbox event scan finished without new findings");
        }
        return discovered;
    }

    private void tryAppendDraftAuditForOutboxEvent(OutboxEventEntity event) {
        Long draftId = parseDraftId(event);
        if (draftId == null || workflowStore.findDraftById(draftId).isEmpty()) {
            return;
        }
        String remark = "eventId=" + event.getEventId()
                + ";eventType=" + event.getEventType()
                + ";aggregateType=" + event.getAggregateType()
                + ";aggregateId=" + event.getAggregateId();
        boolean exists = workflowStore.listPublishLogs(draftId).stream()
                .anyMatch(logEntry -> OUTBOX_INTERVENTION_ACTION.equals(logEntry.getActionType())
                        && remark.equals(logEntry.getRemark()));
        if (exists) {
            return;
        }

        workflowStore.insertPublishLog(WorkflowAuditLogFactory.outboxAction(
                        draftId,
                        event.getId(),
                        event.getAggregateVersion(),
                        OUTBOX_INTERVENTION_ACTION,
                        "xxl-job",
                        "xxl-job"
                )
                .targetType(WorkflowAuditTargetType.OUTBOX_EVENT)
                .targetId(event.getId())
                .result(WorkflowAuditResult.INTERVENTION_REQUIRED)
                .errorCode("OUTBOX_DEAD_SCAN")
                .errorMessage(event.getErrorMessage())
                .remark(remark)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Long parseDraftId(OutboxEventEntity event) {
        if ("content_draft".equals(event.getAggregateType())) {
            return parseLongQuietly(event.getAggregateId());
        }
        return null;
    }

    private Long parseLongQuietly(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String buildTaskMarker(PublishTask task) {
        return "taskId=" + task.getId()
                + ";taskType=" + task.getTaskType()
                + ";version=" + task.getPublishedVersion()
                + ";error=" + safe(task.getErrorMessage());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

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
 * 应用服务实现，负责组织领域对象、存储组件与外部能力完成业务流程编排。
 */
@Service
public class WorkflowReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowReconciliationService.class);
    private static final String TASK_INTERVENTION_ACTION = "TASK_MANUAL_INTERVENTION_REQUIRED";
    private static final String OUTBOX_INTERVENTION_ACTION = "OUTBOX_MANUAL_INTERVENTION_REQUIRED";

    private final WorkflowStore workflowStore;
    private final OutboxEventRepository outboxEventRepository;
    private final CacheManager cacheManager;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param workflowStore 参数 workflowStore 对应的业务输入值
     * @param outboxEventRepository 参数 outboxEventRepository 对应的业务输入值
     * @param cacheManager 参数 cacheManager 对应的业务输入值
     */

    public WorkflowReconciliationService(WorkflowStore workflowStore,
                                         OutboxEventRepository outboxEventRepository,
                                         CacheManager cacheManager) {
        this.workflowStore = workflowStore;
        this.outboxEventRepository = outboxEventRepository;
        this.cacheManager = cacheManager;
    }

    /**
     * 处理 scan dead publish tasks 相关逻辑，并返回对应的执行结果。
     *
     * @param limit 参数 limit 对应的业务输入值
     * @return 统计值或数量结果
     */

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

    /**
     * 处理 scan dead outbox events 相关逻辑，并返回对应的执行结果。
     *
     * @param limit 参数 limit 对应的业务输入值
     * @return 统计值或数量结果
     */

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

    /**
     * 处理 try append draft audit for outbox event 相关逻辑，并返回对应的执行结果。
     *
     * @param event 事件对象
     */

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

    /**
     * 处理 parse draft id 相关逻辑，并返回对应的执行结果。
     *
     * @param event 事件对象
     * @return 统计值或数量结果
     */

    private Long parseDraftId(OutboxEventEntity event) {
        if ("content_draft".equals(event.getAggregateType())) {
            return parseLongQuietly(event.getAggregateId());
        }
        return null;
    }

    /**
     * 处理 parse long quietly 相关逻辑，并返回对应的执行结果。
     *
     * @param raw 参数 raw 对应的业务输入值
     * @return 统计值或数量结果
     */

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

    /**
     * 构建当前场景所需的结果对象或配置内容。
     *
     * @param task 任务对象
     * @return 方法处理后的结果对象
     */

    private String buildTaskMarker(PublishTask task) {
        return "taskId=" + task.getId()
                + ";taskType=" + task.getTaskType()
                + ";version=" + task.getPublishedVersion()
                + ";error=" + safe(task.getErrorMessage());
    }

    /**
     * 处理 safe 相关逻辑，并返回对应的执行结果。
     *
     * @param value 待处理的原始值
     * @return 方法处理后的结果对象
     */

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

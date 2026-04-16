package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.cache.CacheNames;
import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.messaging.outbox.OutboxEventEntity;
import com.contentworkflow.common.messaging.outbox.OutboxEventRepository;
import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.workflow.application.store.PublishLogEntry;
import com.contentworkflow.workflow.application.store.WorkflowAuditLogFactory;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.vo.ManualRecoveryResponse;
import com.contentworkflow.workflow.interfaces.vo.RecoverableOutboxEventResponse;
import com.contentworkflow.workflow.interfaces.vo.RecoverablePublishTaskResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * 应用服务实现，负责组织领域对象、存储组件与外部能力完成业务流程编排。
 */
@Service
public class WorkflowRecoveryService {

    private static final EnumSet<PublishTaskStatus> RECOVERABLE_TASK_STATUSES = EnumSet.of(
            PublishTaskStatus.FAILED,
            PublishTaskStatus.DEAD
    );
    private static final EnumSet<OutboxEventStatus> RECOVERABLE_OUTBOX_STATUSES = EnumSet.of(
            OutboxEventStatus.FAILED,
            OutboxEventStatus.DEAD
    );

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

    public WorkflowRecoveryService(WorkflowStore workflowStore,
                                   OutboxEventRepository outboxEventRepository,
                                   CacheManager cacheManager) {
        this.workflowStore = workflowStore;
        this.outboxEventRepository = outboxEventRepository;
        this.cacheManager = cacheManager;
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @param statuses 参数 statuses 对应的业务输入值
     * @return 符合条件的结果集合
     */

    @Transactional(readOnly = true)
    public List<RecoverablePublishTaskResponse> listRecoverablePublishTasks(Long draftId,
                                                                            Collection<PublishTaskStatus> statuses) {
        ContentDraft draft = workflowStore.findDraftById(draftId)
                .orElseThrow(() -> new BusinessException("DRAFT_NOT_FOUND", "draft not found"));
        EnumSet<PublishTaskStatus> filterStatuses = normalizeTaskStatuses(statuses);

        return workflowStore.listPublishTasks(draftId).stream()
                .filter(task -> filterStatuses.contains(task.getStatus()))
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .map(task -> toRecoverableTaskResponse(task, draft))
                .toList();
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @param statuses 参数 statuses 对应的业务输入值
     * @param limit 参数 limit 对应的业务输入值
     * @return 符合条件的结果集合
     */

    @Transactional(readOnly = true)
    public List<RecoverableOutboxEventResponse> listRecoverableOutboxEvents(Long draftId,
                                                                            Collection<OutboxEventStatus> statuses,
                                                                            int limit) {
        EnumSet<OutboxEventStatus> filterStatuses = normalizeOutboxStatuses(statuses);
        int pageSize = Math.max(1, Math.min(limit, 200));
        PageRequest pageRequest = PageRequest.of(
                0,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        List<OutboxEventEntity> events;
        if (draftId == null) {
            events = outboxEventRepository.findByStatusIn(filterStatuses, pageRequest);
        } else {
            events = outboxEventRepository.findByAggregateTypeAndAggregateIdAndStatusIn(
                    "content_draft",
                    String.valueOf(draftId),
                    filterStatuses,
                    pageRequest
            );
        }

        return events.stream()
                .map(this::toRecoverableOutboxEventResponse)
                .toList();
    }

    /**
     * 处理 retry publish task 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param taskId 相关业务对象的唯一标识
     * @param remark 参数 remark 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Transactional
    public ManualRecoveryResponse retryPublishTask(Long draftId,
                                                   Long taskId,
                                                   String remark,
                                                   WorkflowOperatorIdentity operator) {
        ContentDraft draft = workflowStore.findDraftById(draftId)
                .orElseThrow(() -> new BusinessException("DRAFT_NOT_FOUND", "draft not found"));
        PublishTask task = workflowStore.listPublishTasks(draftId).stream()
                .filter(item -> Objects.equals(item.getId(), taskId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PUBLISH_TASK_NOT_FOUND", "publish task not found"));

        return retryPublishTaskInternal(draft, task, normalizeRemark(remark), operator);
    }

    /**
     * 处理 retry current version publish tasks 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param remark 参数 remark 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @return 符合条件的结果集合
     */

    @Transactional
    public List<ManualRecoveryResponse> retryCurrentVersionPublishTasks(Long draftId,
                                                                        String remark,
                                                                        WorkflowOperatorIdentity operator) {
        ContentDraft draft = workflowStore.findDraftById(draftId)
                .orElseThrow(() -> new BusinessException("DRAFT_NOT_FOUND", "draft not found"));
        String normalizedRemark = normalizeRemark(remark);
        Integer currentVersion = draft.getPublishedVersion();
        List<PublishTask> currentVersionTasks = workflowStore.listPublishTasks(draftId).stream()
                .filter(task -> Objects.equals(task.getPublishedVersion(), currentVersion))
                .filter(task -> RECOVERABLE_TASK_STATUSES.contains(task.getStatus()))
                .sorted((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
                .toList();

        if (currentVersionTasks.isEmpty()) {
            throw new BusinessException("NO_RECOVERABLE_PUBLISH_TASK", "no recoverable publish task found for current publishedVersion");
        }

        List<ManualRecoveryResponse> responses = currentVersionTasks.stream()
                .map(task -> retryPublishTaskInternal(draft, task, normalizedRemark, operator))
                .toList();

        workflowStore.insertPublishLog(WorkflowAuditLogFactory.publishAction(draftId, currentVersion, "TASK_BATCH_MANUAL_RETRIED", operator)
                .beforeStatus(draft.getStatus().name())
                .afterStatus(draft.getStatus().name())
                .result(WorkflowAuditResult.ACCEPTED)
                .remark(buildRemark(
                        "publishedVersion=" + currentVersion + ";count=" + responses.size(),
                        normalizedRemark
                ))
                .createdAt(LocalDateTime.now())
                .build());
        return responses;
    }

    /**
     * 处理 retry outbox event 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param outboxEventId 相关业务对象的唯一标识
     * @param remark 参数 remark 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Transactional
    public ManualRecoveryResponse retryOutboxEvent(Long outboxEventId,
                                                   String remark,
                                                   WorkflowOperatorIdentity operator) {
        OutboxEventEntity event = outboxEventRepository.findById(outboxEventId)
                .orElseThrow(() -> new BusinessException("OUTBOX_EVENT_NOT_FOUND", "outbox event not found"));

        if (!RECOVERABLE_OUTBOX_STATUSES.contains(event.getStatus())) {
            throw new BusinessException("INVALID_OUTBOX_STATUS", "only FAILED or DEAD outbox events can be retried manually");
        }

        OutboxEventStatus beforeStatus = event.getStatus();
        LocalDateTime now = LocalDateTime.now();
        event.setStatus(OutboxEventStatus.NEW);
        event.setAttempt(0);
        event.setNextRetryAt(now);
        event.setLockedBy(null);
        event.setLockedAt(null);
        event.setErrorMessage(null);
        outboxEventRepository.save(event);

        evictDeadOutboxScanMarker(event);

        Long draftId = parseDraftId(event);
        if (draftId != null && workflowStore.findDraftById(draftId).isPresent()) {
            workflowStore.insertPublishLog(WorkflowAuditLogFactory.outboxAction(
                            draftId,
                            outboxEventId,
                            event.getAggregateVersion(),
                            "OUTBOX_MANUAL_RETRIED",
                            operator.operatorId(),
                            operator.operatorName()
                    )
                    .targetType(WorkflowAuditTargetType.OUTBOX_EVENT)
                    .targetId(outboxEventId)
                    .beforeStatus(beforeStatus.name())
                    .afterStatus(event.getStatus().name())
                    .result(WorkflowAuditResult.ACCEPTED)
                    .remark(buildRemark(
                            "outboxEventId=" + outboxEventId + ";from=" + beforeStatus + ";to=" + event.getStatus(),
                            normalizeRemark(remark)
                    ))
                    .createdAt(now)
                    .build());
        }

        return new ManualRecoveryResponse(
                "OUTBOX_EVENT",
                outboxEventId,
                draftId,
                beforeStatus.name(),
                event.getStatus().name(),
                operator.operatorName(),
                normalizeRemark(remark),
                now
        );
    }

    /**
     * 处理 requeue dead publish task 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param taskId 相关业务对象的唯一标识
     * @param remark 参数 remark 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Transactional
    public ManualRecoveryResponse requeueDeadPublishTask(Long draftId,
                                                         Long taskId,
                                                         String remark,
                                                         WorkflowOperatorIdentity operator) {
        return retryPublishTask(draftId, taskId, remark, operator);
    }

    /**
     * 处理 requeue dead outbox event 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param outboxEventId 相关业务对象的唯一标识
     * @param remark 参数 remark 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Transactional
    public ManualRecoveryResponse requeueDeadOutboxEvent(Long outboxEventId,
                                                         String remark,
                                                         WorkflowOperatorIdentity operator) {
        return retryOutboxEvent(outboxEventId, remark, operator);
    }

    /**
     * 处理 retry publish task internal 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draft 草稿对象
     * @param task 任务对象
     * @param normalizedRemark 参数 normalizedRemark 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    private ManualRecoveryResponse retryPublishTaskInternal(ContentDraft draft,
                                                            PublishTask task,
                                                            String normalizedRemark,
                                                            WorkflowOperatorIdentity operator) {
        if (!RECOVERABLE_TASK_STATUSES.contains(task.getStatus())) {
            throw new BusinessException("INVALID_TASK_STATUS", "only FAILED or DEAD publish tasks can be retried manually");
        }
        if (!Objects.equals(task.getPublishedVersion(), draft.getPublishedVersion())) {
            throw new BusinessException("STALE_PUBLISH_TASK", "only tasks from the current publishedVersion can be retried");
        }

        PublishTaskStatus beforeStatus = task.getStatus();
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(PublishTaskStatus.PENDING);
        task.setRetryTimes(0);
        task.setErrorMessage(null);
        task.setNextRunAt(now);
        task.setLockedBy(null);
        task.setLockedAt(null);
        task.setUpdatedAt(now);
        workflowStore.updatePublishTask(task);

        resumeDraftPublishingIfNeeded(draft, now);

        workflowStore.insertPublishLog(WorkflowAuditLogFactory.taskAction(
                        task,
                        beforeStatus == PublishTaskStatus.DEAD ? "TASK_MANUAL_REQUEUED" : "TASK_MANUAL_RETRIED",
                        operator.operatorId(),
                        operator.operatorName()
                )
                .beforeStatus(beforeStatus.name())
                .afterStatus(task.getStatus().name())
                .result(WorkflowAuditResult.ACCEPTED)
                .remark(buildRemark(
                        "taskId=" + task.getId() + ";from=" + beforeStatus + ";to=" + task.getStatus() + ";version=" + task.getPublishedVersion(),
                        normalizedRemark
                ))
                .createdAt(now)
                .build());

        return new ManualRecoveryResponse(
                "PUBLISH_TASK",
                task.getId(),
                draft.getId(),
                beforeStatus.name(),
                task.getStatus().name(),
                operator.operatorName(),
                normalizedRemark,
                now
        );
    }

    /**
     * 处理 resume draft publishing if needed 相关逻辑，并返回对应的执行结果。
     *
     * @param draft 草稿对象
     * @param now 参数 now 对应的业务输入值
     */

    private void resumeDraftPublishingIfNeeded(ContentDraft draft, LocalDateTime now) {
        if (draft.getStatus() == WorkflowStatus.PUBLISH_FAILED) {
            draft.setStatus(WorkflowStatus.PUBLISHING);
            draft.setUpdatedAt(now);
            workflowStore.updateDraft(draft);
        }
    }

    /**
     * 处理 to recoverable task response 相关逻辑，并返回对应的执行结果。
     *
     * @param task 任务对象
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

    private RecoverablePublishTaskResponse toRecoverableTaskResponse(PublishTask task, ContentDraft draft) {
        boolean actionable = RECOVERABLE_TASK_STATUSES.contains(task.getStatus())
                && Objects.equals(task.getPublishedVersion(), draft.getPublishedVersion());
        boolean staleVersion = !Objects.equals(task.getPublishedVersion(), draft.getPublishedVersion());
        return new RecoverablePublishTaskResponse(
                task.getId(),
                task.getPublishedVersion(),
                task.getTaskType(),
                task.getStatus(),
                task.getRetryTimes(),
                task.getErrorMessage(),
                task.getNextRunAt(),
                task.getLockedBy(),
                task.getLockedAt(),
                actionable,
                staleVersion,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    /**
     * 处理 to recoverable outbox event response 相关逻辑，并返回对应的执行结果。
     *
     * @param event 事件对象
     * @return 方法处理后的结果对象
     */

    private RecoverableOutboxEventResponse toRecoverableOutboxEventResponse(OutboxEventEntity event) {
        return new RecoverableOutboxEventResponse(
                event.getId(),
                event.getEventId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getAggregateVersion(),
                event.getStatus(),
                event.getAttempt(),
                event.getErrorMessage(),
                event.getNextRetryAt(),
                event.getLockedBy(),
                event.getLockedAt(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                RECOVERABLE_OUTBOX_STATUSES.contains(event.getStatus())
        );
    }

    /**
     * 对输入值进行标准化处理，便于后续统一使用。
     *
     * @param statuses 参数 statuses 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private EnumSet<PublishTaskStatus> normalizeTaskStatuses(Collection<PublishTaskStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return EnumSet.copyOf(RECOVERABLE_TASK_STATUSES);
        }
        EnumSet<PublishTaskStatus> normalized = EnumSet.copyOf(statuses);
        if (!RECOVERABLE_TASK_STATUSES.containsAll(normalized)) {
            throw new BusinessException("INVALID_ARGUMENT", "task recovery status filter only supports FAILED or DEAD");
        }
        return normalized;
    }

    /**
     * 对输入值进行标准化处理，便于后续统一使用。
     *
     * @param statuses 参数 statuses 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private EnumSet<OutboxEventStatus> normalizeOutboxStatuses(Collection<OutboxEventStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return EnumSet.copyOf(RECOVERABLE_OUTBOX_STATUSES);
        }
        EnumSet<OutboxEventStatus> normalized = EnumSet.copyOf(statuses);
        if (!RECOVERABLE_OUTBOX_STATUSES.containsAll(normalized)) {
            throw new BusinessException("INVALID_ARGUMENT", "outbox recovery status filter only supports FAILED or DEAD");
        }
        return normalized;
    }

    /**
     * 处理 evict dead outbox scan marker 相关逻辑，并返回对应的执行结果。
     *
     * @param event 事件对象
     */

    private void evictDeadOutboxScanMarker(OutboxEventEntity event) {
        Cache cache = cacheManager.getCache(CacheNames.DEAD_OUTBOX_SCAN_MARKER);
        if (cache == null) {
            return;
        }
        String markerKey = event.getEventId() == null ? "outbox:" + event.getId() : event.getEventId();
        cache.evict(markerKey);
    }

    /**
     * 处理 parse draft id 相关逻辑，并返回对应的执行结果。
     *
     * @param event 事件对象
     * @return 统计值或数量结果
     */

    private Long parseDraftId(OutboxEventEntity event) {
        if (!"content_draft".equals(event.getAggregateType())) {
            return null;
        }
        try {
            return Long.parseLong(event.getAggregateId());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 构建当前场景所需的结果对象或配置内容。
     *
     * @param base 参数 base 对应的业务输入值
     * @param remark 参数 remark 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private String buildRemark(String base, String remark) {
        String normalized = normalizeRemark(remark);
        return normalized == null ? base : base + " | " + normalized;
    }

    /**
     * 对输入值进行标准化处理，便于后续统一使用。
     *
     * @param remark 参数 remark 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private String normalizeRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            return null;
        }
        return remark.trim();
    }
}

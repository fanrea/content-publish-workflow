package com.contentworkflow.workflow.application.store;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.common.cache.CacheNames;
import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.entity.ReviewRecord;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.DraftOperationLockEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishCommandEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishLogEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordEntity;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ContentDraftMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ContentSnapshotMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.DraftOperationLockMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.DraftStatusCountRow;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishCommandMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishLogMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishTaskMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ReviewRecordMybatisMapper;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Primary
@Component
public class MybatisWorkflowStore implements WorkflowStore {

    private final ContentDraftMybatisMapper draftMapper;
    private final ReviewRecordMybatisMapper reviewMapper;
    private final ContentSnapshotMybatisMapper snapshotMapper;
    private final PublishTaskMybatisMapper taskMapper;
    private final PublishLogMybatisMapper logMapper;
    private final PublishCommandMybatisMapper commandMapper;
    private final DraftOperationLockMybatisMapper operationLockMapper;

    public MybatisWorkflowStore(ContentDraftMybatisMapper draftMapper,
                                ReviewRecordMybatisMapper reviewMapper,
                                ContentSnapshotMybatisMapper snapshotMapper,
                                PublishTaskMybatisMapper taskMapper,
                                PublishLogMybatisMapper logMapper,
                                PublishCommandMybatisMapper commandMapper,
                                DraftOperationLockMybatisMapper operationLockMapper) {
        this.draftMapper = draftMapper;
        this.reviewMapper = reviewMapper;
        this.snapshotMapper = snapshotMapper;
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.commandMapper = commandMapper;
        this.operationLockMapper = operationLockMapper;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.DRAFT_LIST_LATEST,
            key = "T(com.contentworkflow.common.cache.CacheKeys).all()",
            unless = "#result == null || #result.isEmpty()")
    public List<ContentDraft> listDrafts() {
        return draftMapper.selectAllOrderByUpdatedAtDesc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContentDraft> pageDrafts(DraftQueryRequest request) {
        DraftQueryRequest normalized = normalizeRequest(request);
        int pageNo = normalized.pageNo();
        int pageSize = normalized.pageSize();
        int offset = Math.max(0, pageNo - 1) * pageSize;

        List<ContentDraft> items = draftMapper.selectPage(
                        normalized,
                        offset,
                        pageSize,
                        toSortColumn(normalized),
                        normalized.sortDirection().name()
                ).stream()
                .map(this::toDomain)
                .toList();
        long total = draftMapper.countPage(normalized);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return new PageResponse<>(items, total, pageNo, pageSize, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<WorkflowStatus, Long> countDraftsByStatus(DraftQueryRequest request) {
        EnumMap<WorkflowStatus, Long> result = new EnumMap<>(WorkflowStatus.class);
        for (DraftStatusCountRow row : draftMapper.countByStatus(normalizeRequest(request))) {
            result.put(row.getStatus(), row.getCnt() == null ? 0L : row.getCnt());
        }
        return result;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DRAFT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID, key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#result.id)", condition = "#result != null && #result.id != null"),
            @CacheEvict(cacheNames = CacheNames.DRAFT_STATUS_COUNT, allEntries = true)
    })
    public ContentDraft insertDraft(ContentDraft draft) {
        ContentDraftEntity entity = new ContentDraftEntity();
        entity.setVersion(draft.getVersion());
        entity.setBizNo(draft.getBizNo() == null || draft.getBizNo().isBlank()
                ? "CPW-" + UUID.randomUUID().toString().replace("-", "")
                : draft.getBizNo());
        entity.setTitle(draft.getTitle());
        entity.setSummary(draft.getSummary());
        entity.setBody(draft.getBody());
        entity.setDraftVersion(draft.getDraftVersion());
        entity.setPublishedVersion(draft.getPublishedVersion());
        entity.setWorkflowStatus(draft.getStatus());
        entity.setCurrentSnapshotId(draft.getCurrentSnapshotId());
        entity.setLastReviewComment(draft.getLastReviewComment());
        entity.setCreatedAt(draft.getCreatedAt());
        entity.setUpdatedAt(draft.getUpdatedAt());
        entity.prepareForInsert();
        draftMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID,
            key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#draftId)",
            unless = "#result == null || !#result.isPresent()")
    public Optional<ContentDraft> findDraftById(Long draftId) {
        return draftMapper.selectById(draftId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DraftOperationLockEntry> findDraftOperationLock(Long draftId) {
        return operationLockMapper.selectByDraftId(draftId).map(this::toLockEntry);
    }

    @Override
    @Transactional
    public boolean tryAcquireDraftOperationLock(DraftOperationLockEntry lockEntry, LocalDateTime now) {
        if (lockEntry == null || lockEntry.getDraftId() == null) {
            throw new IllegalArgumentException("lockEntry/draftId required");
        }
        try {
            return operationLockMapper.insertLock(
                    lockEntry.getDraftId(),
                    lockEntry.getOperationType().name(),
                    lockEntry.getTargetPublishedVersion(),
                    lockEntry.getLockedBy(),
                    lockEntry.getLockedAt(),
                    lockEntry.getExpiresAt()
            ) > 0;
        } catch (DataIntegrityViolationException ex) {
            return operationLockMapper.replaceExpiredLock(
                    lockEntry.getDraftId(),
                    lockEntry.getOperationType(),
                    lockEntry.getTargetPublishedVersion(),
                    lockEntry.getLockedBy(),
                    lockEntry.getLockedAt(),
                    lockEntry.getExpiresAt(),
                    now
            ) > 0;
        }
    }

    @Override
    @Transactional
    public boolean renewDraftOperationLock(Long draftId,
                                           Integer targetPublishedVersion,
                                           String lockedBy,
                                           LocalDateTime lockedAt,
                                           LocalDateTime expiresAt) {
        if (draftId == null) {
            throw new IllegalArgumentException("draftId required");
        }
        return operationLockMapper.renewLock(draftId, targetPublishedVersion, lockedBy, lockedAt, expiresAt) > 0;
    }

    @Override
    @Transactional
    public boolean releaseDraftOperationLock(Long draftId, Integer targetPublishedVersion) {
        if (draftId == null) {
            throw new IllegalArgumentException("draftId required");
        }
        return operationLockMapper.deleteMatchingLock(draftId, targetPublishedVersion) > 0;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID, key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#draft.id)", condition = "#draft != null && #draft.id != null"),
            @CacheEvict(cacheNames = CacheNames.DRAFT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DRAFT_STATUS_COUNT, allEntries = true)
    })
    public ContentDraft updateDraft(ContentDraft draft, EnumSet<WorkflowStatus> expectedStatuses) {
        EnumSet<WorkflowStatus> requiredStatuses = normalizeExpectedStatuses(expectedStatuses);
        int updated = draftMapper.conditionalUpdate(
                draft.getId(),
                draft.getVersion(),
                requiredStatuses,
                draft.getBizNo(),
                draft.getTitle(),
                draft.getSummary(),
                draft.getBody(),
                draft.getDraftVersion(),
                draft.getPublishedVersion(),
                draft.getStatus(),
                draft.getCurrentSnapshotId(),
                draft.getLastReviewComment(),
                draft.getUpdatedAt()
        );
        if (updated == 0) {
            throw resolveConditionalUpdateFailure(draft, requiredStatuses);
        }
        return draftMapper.selectById(draft.getId())
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalStateException("draft not found after update: " + draft.getId()));
    }

    @Override
    @Transactional
    public ReviewRecord insertReviewRecord(ReviewRecord record) {
        ReviewRecordEntity entity = new ReviewRecordEntity();
        entity.setDraftId(record.getDraftId());
        entity.setDraftVersion(record.getDraftVersion());
        entity.setReviewer(record.getReviewer());
        entity.setDecision(record.getDecision());
        entity.setComment(record.getComment());
        entity.setReviewedAt(record.getReviewedAt());
        entity.prepareForInsert();
        reviewMapper.insert(entity);
        return toReviewRecord(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewRecord> listReviewRecords(Long draftId) {
        return reviewMapper.selectByDraftIdOrderByReviewedAtDesc(draftId).stream()
                .map(this::toReviewRecord)
                .toList();
    }

    @Override
    @Transactional
    public ContentSnapshot insertSnapshot(ContentSnapshot snapshot) {
        ContentSnapshotEntity entity = new ContentSnapshotEntity();
        entity.setDraftId(snapshot.getDraftId());
        entity.setPublishedVersion(snapshot.getPublishedVersion());
        entity.setSourceDraftVersion(snapshot.getSourceDraftVersion());
        entity.setTitle(snapshot.getTitle());
        entity.setSummary(snapshot.getSummary());
        entity.setBody(snapshot.getBody());
        entity.setOperatorName(snapshot.getOperator());
        entity.setRollback(snapshot.isRollback());
        entity.setPublishedAt(snapshot.getPublishedAt());
        entity.prepareForInsert();
        snapshotMapper.insert(entity);
        return toSnapshot(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentSnapshot> listSnapshots(Long draftId) {
        return snapshotMapper.selectByDraftIdOrderByPublishedVersionDesc(draftId).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    @Transactional
    public List<PublishTask> insertPublishTasks(List<PublishTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        return tasks.stream()
                .map(task -> {
                    PublishTaskEntity entity = new PublishTaskEntity();
                    entity.setDraftId(task.getDraftId());
                    entity.setPublishedVersion(task.getPublishedVersion());
                    entity.setTaskType(task.getTaskType());
                    entity.setStatus(task.getStatus());
                    entity.setRetryTimes(task.getRetryTimes());
                    entity.setErrorMessage(task.getErrorMessage());
                    entity.setNextRunAt(task.getNextRunAt());
                    entity.setLockedBy(task.getLockedBy());
                    entity.setLockedAt(task.getLockedAt());
                    entity.setCreatedAt(task.getCreatedAt());
                    entity.setUpdatedAt(task.getUpdatedAt());
                    entity.prepareForInsert();
                    taskMapper.insert(entity);
                    return toPublishTask(entity);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishTask> listPublishTasks(Long draftId) {
        return taskMapper.selectByDraftIdOrderByUpdatedAtDesc(draftId).stream()
                .map(this::toPublishTask)
                .toList();
    }

    @Override
    @Transactional
    public PublishTask updatePublishTask(PublishTask task) {
        PublishTaskEntity entity = taskMapper.selectById(task.getId())
                .orElseThrow(() -> new IllegalStateException("task not found: " + task.getId()));
        entity.setStatus(task.getStatus());
        entity.setRetryTimes(task.getRetryTimes());
        entity.setErrorMessage(task.getErrorMessage());
        entity.setNextRunAt(task.getNextRunAt());
        entity.setLockedBy(task.getLockedBy());
        entity.setLockedAt(task.getLockedAt());
        entity.setUpdatedAt(task.getUpdatedAt());
        taskMapper.update(entity);
        return toPublishTask(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishTask> listPublishTasksByStatus(PublishTaskStatus status) {
        return taskMapper.selectByStatusOrderByUpdatedAtAsc(status).stream()
                .map(this::toPublishTask)
                .toList();
    }

    @Override
    @Transactional
    public List<PublishTask> claimRunnablePublishTasks(int limit, String workerId, LocalDateTime now, int lockSeconds) {
        if (limit <= 0) {
            return List.of();
        }
        LocalDateTime lockExpiredBefore = now.minusSeconds(Math.max(1, lockSeconds));
        List<PublishTaskEntity> runnable = taskMapper.selectRunnableForUpdate(now, lockExpiredBefore, limit);
        if (runnable.isEmpty()) {
            return List.of();
        }
        for (PublishTaskEntity entity : runnable) {
            entity.setStatus(PublishTaskStatus.RUNNING);
            entity.setLockedBy(workerId);
            entity.setLockedAt(now);
            entity.setUpdatedAt(now);
            taskMapper.update(entity);
        }
        return runnable.stream().map(this::toPublishTask).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishCommandEntry> findPublishCommand(Long draftId, String commandType, String idempotencyKey) {
        if (draftId == null || commandType == null || idempotencyKey == null) {
            return Optional.empty();
        }
        return commandMapper.selectByUniqueKey(draftId, commandType, idempotencyKey).map(this::toCommandEntry);
    }

    @Override
    @Transactional
    public boolean tryCreatePublishCommand(PublishCommandEntry entry) {
        if (entry == null || entry.getDraftId() == null || entry.getCommandType() == null || entry.getIdempotencyKey() == null) {
            throw new IllegalArgumentException("draftId/commandType/idempotencyKey required");
        }
        try {
            PublishCommandEntity entity = toCommandEntity(entry);
            entity.prepareForInsert();
            commandMapper.insert(entity);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Override
    @Transactional
    public PublishCommandEntry updatePublishCommand(PublishCommandEntry entry) {
        if (entry == null || entry.getDraftId() == null || entry.getCommandType() == null || entry.getIdempotencyKey() == null) {
            throw new IllegalArgumentException("draftId/commandType/idempotencyKey required");
        }
        PublishCommandEntity entity = commandMapper.selectByUniqueKey(entry.getDraftId(), entry.getCommandType(), entry.getIdempotencyKey())
                .orElseThrow(() -> new IllegalStateException("publish command not found"));
        entity.setOperatorName(entry.getOperatorName());
        entity.setRemark(entry.getRemark());
        entity.setCommandStatus(entry.getStatus());
        entity.setTargetPublishedVersion(entry.getTargetPublishedVersion());
        entity.setSnapshotId(entry.getSnapshotId());
        entity.setErrorMessage(entry.getErrorMessage());
        entity.setUpdatedAt(entry.getUpdatedAt());
        commandMapper.update(entity);
        return toCommandEntry(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishCommandEntry> listPublishCommands(Long draftId) {
        return commandMapper.selectByDraftIdOrderByCreatedAtDesc(draftId).stream()
                .map(this::toCommandEntry)
                .toList();
    }

    @Override
    @Transactional
    public PublishLogEntry insertPublishLog(PublishLogEntry entry) {
        PublishLogEntity entity = new PublishLogEntity();
        entity.setDraftId(entry.getDraftId());
        entity.setTraceId(entry.getTraceId());
        entity.setRequestId(entry.getRequestId());
        entity.setActionType(entry.getActionType());
        entity.setOperatorId(entry.getOperatorId());
        entity.setOperatorName(entry.getOperatorName());
        entity.setTargetType(entry.getTargetType());
        entity.setTargetId(entry.getTargetId());
        entity.setPublishedVersion(entry.getPublishedVersion());
        entity.setTaskId(entry.getTaskId());
        entity.setOutboxEventId(entry.getOutboxEventId());
        entity.setBeforeStatus(entry.getBeforeStatus());
        entity.setAfterStatus(entry.getAfterStatus());
        entity.setResult(entry.getResult());
        entity.setErrorCode(entry.getErrorCode());
        entity.setErrorMessage(entry.getErrorMessage());
        entity.setRemark(entry.getRemark());
        entity.setCreatedAt(entry.getCreatedAt());
        entity.prepareForInsert();
        logMapper.insert(entity);
        return toPublishLogEntry(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishLogEntry> listPublishLogs(Long draftId) {
        return logMapper.selectByDraftIdOrderByCreatedAtDesc(draftId).stream()
                .map(this::toPublishLogEntry)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishLogEntry> listPublishLogsByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return logMapper.selectByTraceIdOrderByCreatedAtAsc(traceId).stream()
                .map(this::toPublishLogEntry)
                .toList();
    }

    private DraftQueryRequest normalizeRequest(DraftQueryRequest request) {
        if (request == null) {
            return new DraftQueryRequest(null, null, false, 1, 20,
                    DraftQueryRequest.DraftSortBy.UPDATED_AT,
                    DraftQueryRequest.SortDirection.DESC,
                    null, null, null, null);
        }
        return request.normalized();
    }

    private String toSortColumn(DraftQueryRequest request) {
        return switch (request.sortBy()) {
            case ID -> "id";
            case CREATED_AT -> "created_at";
            case UPDATED_AT -> "updated_at";
        };
    }

    private EnumSet<WorkflowStatus> normalizeExpectedStatuses(EnumSet<WorkflowStatus> expectedStatuses) {
        if (expectedStatuses == null || expectedStatuses.isEmpty()) {
            return EnumSet.allOf(WorkflowStatus.class);
        }
        return EnumSet.copyOf(expectedStatuses);
    }

    private RuntimeException resolveConditionalUpdateFailure(ContentDraft draft, EnumSet<WorkflowStatus> expectedStatuses) {
        ContentDraftEntity current = draftMapper.selectById(draft.getId())
                .orElseThrow(() -> new IllegalStateException("draft not found: " + draft.getId()));
        if (draft.getVersion() == null || !draft.getVersion().equals(current.getVersion())) {
            throw concurrentModification(draft.getId(), draft.getVersion(), current.getVersion());
        }
        if (!expectedStatuses.contains(current.getWorkflowStatus())) {
            throw invalidWorkflowState(expectedStatuses, current.getWorkflowStatus());
        }
        return new IllegalStateException("conditional draft update affected 0 rows unexpectedly");
    }

    private BusinessException concurrentModification(Long draftId, Long expectedVersion, Long actualVersion) {
        return new BusinessException(
                "CONCURRENT_MODIFICATION",
                "draft changed concurrently"
                        + (draftId == null ? "" : ", draftId=" + draftId)
                        + (expectedVersion == null ? "" : ", expectedVersion=" + expectedVersion)
                        + (actualVersion == null ? "" : ", actualVersion=" + actualVersion)
        );
    }

    private BusinessException invalidWorkflowState(EnumSet<WorkflowStatus> expectedStatuses, WorkflowStatus actualStatus) {
        return new BusinessException(
                "INVALID_WORKFLOW_STATE",
                "draft state precondition failed, expected one of " + expectedStatuses + ", current state: " + actualStatus
        );
    }

    private ContentDraft toDomain(ContentDraftEntity entity) {
        return ContentDraft.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .bizNo(entity.getBizNo())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .body(entity.getBody())
                .draftVersion(entity.getDraftVersion())
                .publishedVersion(entity.getPublishedVersion())
                .status(entity.getWorkflowStatus())
                .currentSnapshotId(entity.getCurrentSnapshotId())
                .lastReviewComment(entity.getLastReviewComment())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ReviewRecord toReviewRecord(ReviewRecordEntity entity) {
        return ReviewRecord.builder()
                .id(entity.getId())
                .draftId(entity.getDraftId())
                .draftVersion(entity.getDraftVersion())
                .reviewer(entity.getReviewer())
                .decision(entity.getDecision())
                .comment(entity.getComment())
                .reviewedAt(entity.getReviewedAt())
                .build();
    }

    private ContentSnapshot toSnapshot(ContentSnapshotEntity entity) {
        return ContentSnapshot.builder()
                .id(entity.getId())
                .draftId(entity.getDraftId())
                .publishedVersion(entity.getPublishedVersion())
                .sourceDraftVersion(entity.getSourceDraftVersion())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .body(entity.getBody())
                .operator(entity.getOperatorName())
                .rollback(entity.isRollback())
                .publishedAt(entity.getPublishedAt())
                .build();
    }

    private PublishTask toPublishTask(PublishTaskEntity entity) {
        return PublishTask.builder()
                .id(entity.getId())
                .draftId(entity.getDraftId())
                .publishedVersion(entity.getPublishedVersion())
                .taskType(entity.getTaskType())
                .status(entity.getStatus())
                .retryTimes(entity.getRetryTimes())
                .errorMessage(entity.getErrorMessage())
                .nextRunAt(entity.getNextRunAt())
                .lockedBy(entity.getLockedBy())
                .lockedAt(entity.getLockedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PublishCommandEntity toCommandEntity(PublishCommandEntry entry) {
        PublishCommandEntity entity = new PublishCommandEntity();
        entity.setDraftId(entry.getDraftId());
        entity.setCommandType(entry.getCommandType());
        entity.setIdempotencyKey(entry.getIdempotencyKey());
        entity.setOperatorName(entry.getOperatorName());
        entity.setRemark(entry.getRemark());
        entity.setCommandStatus(entry.getStatus());
        entity.setTargetPublishedVersion(entry.getTargetPublishedVersion());
        entity.setSnapshotId(entry.getSnapshotId());
        entity.setErrorMessage(entry.getErrorMessage());
        entity.setCreatedAt(entry.getCreatedAt());
        entity.setUpdatedAt(entry.getUpdatedAt());
        return entity;
    }

    private PublishCommandEntry toCommandEntry(PublishCommandEntity entity) {
        return PublishCommandEntry.builder()
                .id(entity.getId())
                .draftId(entity.getDraftId())
                .commandType(entity.getCommandType())
                .idempotencyKey(entity.getIdempotencyKey())
                .operatorName(entity.getOperatorName())
                .remark(entity.getRemark())
                .status(entity.getCommandStatus())
                .targetPublishedVersion(entity.getTargetPublishedVersion())
                .snapshotId(entity.getSnapshotId())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PublishLogEntry toPublishLogEntry(PublishLogEntity entity) {
        return PublishLogEntry.builder()
                .id(entity.getId())
                .draftId(entity.getDraftId())
                .traceId(entity.getTraceId())
                .requestId(entity.getRequestId())
                .actionType(entity.getActionType())
                .operatorId(entity.getOperatorId())
                .operatorName(entity.getOperatorName())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .publishedVersion(entity.getPublishedVersion())
                .taskId(entity.getTaskId())
                .outboxEventId(entity.getOutboxEventId())
                .beforeStatus(entity.getBeforeStatus())
                .afterStatus(entity.getAfterStatus())
                .result(entity.getResult())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private DraftOperationLockEntry toLockEntry(DraftOperationLockEntity entity) {
        return DraftOperationLockEntry.builder()
                .draftId(entity.getDraftId())
                .operationType(entity.getOperationType())
                .targetPublishedVersion(entity.getTargetPublishedVersion())
                .lockedBy(entity.getLockedBy())
                .lockedAt(entity.getLockedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }
}

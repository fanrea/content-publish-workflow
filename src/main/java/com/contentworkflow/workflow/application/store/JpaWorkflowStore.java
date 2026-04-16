package com.contentworkflow.workflow.application.store;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.entity.ReviewRecord;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftJpaEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotJpaEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishCommandJpaEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishLogJpaEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskJpaEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordJpaEntity;
import com.contentworkflow.workflow.infrastructure.persistence.mapper.PublishTaskMapper;
import com.contentworkflow.workflow.infrastructure.persistence.repository.ContentDraftJpaRepository;
import com.contentworkflow.workflow.infrastructure.persistence.repository.ContentSnapshotJpaRepository;
import com.contentworkflow.workflow.infrastructure.persistence.repository.PublishCommandJpaRepository;
import com.contentworkflow.workflow.infrastructure.persistence.repository.PublishLogJpaRepository;
import com.contentworkflow.workflow.infrastructure.persistence.repository.PublishTaskJpaRepository;
import com.contentworkflow.workflow.infrastructure.persistence.repository.ReviewRecordJpaRepository;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 存储抽象，用于统一封装业务对象的持久化读写与查询访问。
 */
@Primary
@Component
public class JpaWorkflowStore implements WorkflowStore {

    private final ContentDraftJpaRepository draftRepo;
    private final ReviewRecordJpaRepository reviewRepo;
    private final ContentSnapshotJpaRepository snapshotRepo;
    private final PublishTaskJpaRepository taskRepo;
    private final PublishLogJpaRepository logRepo;
    private final PublishCommandJpaRepository commandRepo;
    private final EntityManager entityManager;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param draftRepo 参数 draftRepo 对应的业务输入值
     * @param reviewRepo 参数 reviewRepo 对应的业务输入值
     * @param snapshotRepo 参数 snapshotRepo 对应的业务输入值
     * @param taskRepo 参数 taskRepo 对应的业务输入值
     * @param logRepo 参数 logRepo 对应的业务输入值
     * @param commandRepo 参数 commandRepo 对应的业务输入值
     * @param entityManager 参数 entityManager 对应的业务输入值
     */

    public JpaWorkflowStore(ContentDraftJpaRepository draftRepo,
                            ReviewRecordJpaRepository reviewRepo,
                            ContentSnapshotJpaRepository snapshotRepo,
                            PublishTaskJpaRepository taskRepo,
                            PublishLogJpaRepository logRepo,
                            PublishCommandJpaRepository commandRepo,
                            EntityManager entityManager) {
        this.draftRepo = draftRepo;
        this.reviewRepo = reviewRepo;
        this.snapshotRepo = snapshotRepo;
        this.taskRepo = taskRepo;
        this.logRepo = logRepo;
        this.commandRepo = commandRepo;
        this.entityManager = entityManager;
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<ContentDraft> listDrafts() {
        return draftRepo.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 按分页条件查询数据，并返回包含分页元信息的结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 包含分页数据和分页元信息的结果对象
     */

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContentDraft> pageDrafts(DraftQueryRequest request) {
        // pageNo is 1-based in the API, but PageRequest is 0-based.
        int pageNo = request.pageNo();
        int pageSize = request.pageSize();
        PageRequest pageable = PageRequest.of(Math.max(0, pageNo - 1), pageSize, toSort(request));

        Specification<ContentDraftJpaEntity> spec = buildDraftSpec(request);
        Page<ContentDraftJpaEntity> page = draftRepo.findAll(spec, pageable);

        List<ContentDraft> items = page.getContent().stream()
                .map(this::toDomain)
                .toList();
        return new PageResponse<>(items, page.getTotalElements(), pageNo, pageSize, page.getTotalPages());
    }

    /**
     * 统计满足条件的数据数量，用于计数或监控场景。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional(readOnly = true)
    public Map<WorkflowStatus, Long> countDraftsByStatus(DraftQueryRequest request) {
        // Aggregate in the database instead of grouping the result set in memory.
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<ContentDraftJpaEntity> root = cq.from(ContentDraftJpaEntity.class);

        Specification<ContentDraftJpaEntity> spec = buildDraftSpec(request);
        Predicate where = spec.toPredicate(root, cq, cb);
        if (where != null) {
            cq.where(where);
        }

        cq.groupBy(root.get("workflowStatus"));
        cq.multiselect(
                root.get("workflowStatus").alias("status"),
                cb.count(root).alias("cnt")
        );

        List<Tuple> rows = entityManager.createQuery(cq).getResultList();
        EnumMap<WorkflowStatus, Long> result = new EnumMap<>(WorkflowStatus.class);
        for (Tuple row : rows) {
            WorkflowStatus status = row.get("status", WorkflowStatus.class);
            Long count = row.get("cnt", Long.class);
            result.put(status, count == null ? 0L : count);
        }
        return result;
    }

    /**
     * 处理 insert draft 相关逻辑，并返回对应的执行结果。
     *
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraft insertDraft(ContentDraft draft) {
        ContentDraftJpaEntity entity = new ContentDraftJpaEntity();
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

        ContentDraftJpaEntity saved = draftRepo.save(entity);
        return toDomain(saved);
    }

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    @Override
    @Transactional(readOnly = true)
    public Optional<ContentDraft> findDraftById(Long draftId) {
        return draftRepo.findById(draftId).map(this::toDomain);
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraft updateDraft(ContentDraft draft) {
        ContentDraftJpaEntity entity = draftRepo.findById(draft.getId())
                .orElseThrow(() -> new IllegalStateException("draft not found: " + draft.getId()));
        entity.setTitle(draft.getTitle());
        entity.setSummary(draft.getSummary());
        entity.setBody(draft.getBody());
        entity.setDraftVersion(draft.getDraftVersion());
        entity.setPublishedVersion(draft.getPublishedVersion());
        entity.setWorkflowStatus(draft.getStatus());
        entity.setCurrentSnapshotId(draft.getCurrentSnapshotId());
        entity.setLastReviewComment(draft.getLastReviewComment());
        entity.setUpdatedAt(draft.getUpdatedAt());
        ContentDraftJpaEntity saved = draftRepo.save(entity);
        return toDomain(saved);
    }

    /**
     * 处理 insert review record 相关逻辑，并返回对应的执行结果。
     *
     * @param record 记录对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ReviewRecord insertReviewRecord(ReviewRecord record) {
        ReviewRecordJpaEntity entity = new ReviewRecordJpaEntity();
        entity.setDraftId(record.getDraftId());
        entity.setDraftVersion(record.getDraftVersion());
        entity.setReviewer(record.getReviewer());
        entity.setDecision(record.getDecision());
        entity.setComment(record.getComment());
        entity.setReviewedAt(record.getReviewedAt());
        ReviewRecordJpaEntity saved = reviewRepo.save(entity);
        return toDomain(saved);
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<ReviewRecord> listReviewRecords(Long draftId) {
        return reviewRepo.findByDraftIdOrderByReviewedAtDesc(draftId).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 处理 insert snapshot 相关逻辑，并返回对应的执行结果。
     *
     * @param snapshot 参数 snapshot 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentSnapshot insertSnapshot(ContentSnapshot snapshot) {
        ContentSnapshotJpaEntity entity = new ContentSnapshotJpaEntity();
        entity.setDraftId(snapshot.getDraftId());
        entity.setPublishedVersion(snapshot.getPublishedVersion());
        entity.setSourceDraftVersion(snapshot.getSourceDraftVersion());
        entity.setTitle(snapshot.getTitle());
        entity.setSummary(snapshot.getSummary());
        entity.setBody(snapshot.getBody());
        entity.setOperatorName(snapshot.getOperator());
        entity.setRollback(snapshot.isRollback());
        entity.setPublishedAt(snapshot.getPublishedAt());
        ContentSnapshotJpaEntity saved = snapshotRepo.save(entity);
        return toDomain(saved);
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<ContentSnapshot> listSnapshots(Long draftId) {
        return snapshotRepo.findByDraftIdOrderByPublishedVersionDesc(draftId).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 处理 insert publish tasks 相关逻辑，并返回对应的执行结果。
     *
     * @param tasks 参数 tasks 对应的业务输入值
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional
    public List<PublishTask> insertPublishTasks(List<PublishTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<PublishTaskJpaEntity> entities = tasks.stream().map(task -> {
            PublishTaskJpaEntity entity = new PublishTaskJpaEntity();
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
            return entity;
        }).toList();
        List<PublishTaskJpaEntity> saved = taskRepo.saveAll(entities);
        return saved.stream().map(this::toDomain).toList();
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<PublishTask> listPublishTasks(Long draftId) {
        return taskRepo.findByDraftIdOrderByUpdatedAtDesc(draftId).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param task 任务对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public PublishTask updatePublishTask(PublishTask task) {
        PublishTaskJpaEntity entity = taskRepo.findById(task.getId())
                .orElseThrow(() -> new IllegalStateException("task not found: " + task.getId()));
        entity.setStatus(task.getStatus());
        entity.setRetryTimes(task.getRetryTimes());
        entity.setErrorMessage(task.getErrorMessage());
        entity.setNextRunAt(task.getNextRunAt());
        entity.setLockedBy(task.getLockedBy());
        entity.setLockedAt(task.getLockedAt());
        entity.setUpdatedAt(task.getUpdatedAt());
        return toDomain(taskRepo.save(entity));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param status 状态值
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<PublishTask> listPublishTasksByStatus(com.contentworkflow.workflow.domain.enums.PublishTaskStatus status) {
        return taskRepo.findByStatusOrderByUpdatedAtAsc(status).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 处理 claim runnable publish tasks 相关逻辑，并返回对应的执行结果。
     *
     * @param limit 参数 limit 对应的业务输入值
     * @param workerId 相关业务对象的唯一标识
     * @param now 参数 now 对应的业务输入值
     * @param lockSeconds 参数 lockSeconds 对应的业务输入值
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional
    public List<PublishTask> claimRunnablePublishTasks(int limit, String workerId, LocalDateTime now, int lockSeconds) {
        if (limit <= 0) {
            return List.of();
        }
        // Claim and mark tasks as RUNNING in one transaction to reduce double-claiming.
        LocalDateTime lockExpiredBefore = now.minusSeconds(Math.max(1, lockSeconds));
        List<PublishTaskJpaEntity> runnable = taskRepo.findRunnableForUpdate(now, lockExpiredBefore, PageRequest.of(0, limit));
        if (runnable.isEmpty()) {
            return List.of();
        }
        for (PublishTaskJpaEntity entity : runnable) {
            entity.setStatus(com.contentworkflow.workflow.domain.enums.PublishTaskStatus.RUNNING);
            entity.setLockedBy(workerId);
            entity.setLockedAt(now);
            entity.setUpdatedAt(now);
        }
        return taskRepo.saveAll(runnable).stream().map(this::toDomain).toList();
    }

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @param commandType 参数 commandType 对应的业务输入值
     * @param idempotencyKey 参数 idempotencyKey 对应的业务输入值
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishCommandEntry> findPublishCommand(Long draftId, String commandType, String idempotencyKey) {
        if (draftId == null || commandType == null || idempotencyKey == null) {
            return Optional.empty();
        }
        return commandRepo.findByDraftIdAndCommandTypeAndIdempotencyKey(draftId, commandType, idempotencyKey)
                .map(this::toCommandEntry);
    }

    /**
     * 处理 try create publish command 相关逻辑，并返回对应的执行结果。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    @Override
    @Transactional
    public boolean tryCreatePublishCommand(PublishCommandEntry entry) {
        if (entry == null || entry.getDraftId() == null || entry.getCommandType() == null || entry.getIdempotencyKey() == null) {
            throw new IllegalArgumentException("draftId/commandType/idempotencyKey required");
        }

        try {
            PublishCommandJpaEntity entity = new PublishCommandJpaEntity();
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
            commandRepo.save(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Concurrent or duplicate insert for the same (draftId, commandType, idempotencyKey).
            return false;
        }
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public PublishCommandEntry updatePublishCommand(PublishCommandEntry entry) {
        if (entry == null || entry.getDraftId() == null || entry.getCommandType() == null || entry.getIdempotencyKey() == null) {
            throw new IllegalArgumentException("draftId/commandType/idempotencyKey required");
        }
        PublishCommandJpaEntity entity = commandRepo.findByDraftIdAndCommandTypeAndIdempotencyKey(entry.getDraftId(), entry.getCommandType(), entry.getIdempotencyKey())
                .orElseThrow(() -> new IllegalStateException("publish command not found"));
        entity.setOperatorName(entry.getOperatorName());
        entity.setRemark(entry.getRemark());
        entity.setCommandStatus(entry.getStatus());
        entity.setTargetPublishedVersion(entry.getTargetPublishedVersion());
        entity.setSnapshotId(entry.getSnapshotId());
        entity.setErrorMessage(entry.getErrorMessage());
        entity.setUpdatedAt(entry.getUpdatedAt());
        return toCommandEntry(commandRepo.save(entity));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<PublishCommandEntry> listPublishCommands(Long draftId) {
        return commandRepo.findByDraftIdOrderByCreatedAtDesc(draftId).stream()
                .map(this::toCommandEntry)
                .toList();
    }

    /**
     * 处理 insert publish log 相关逻辑，并返回对应的执行结果。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public PublishLogEntry insertPublishLog(PublishLogEntry entry) {
        PublishLogJpaEntity entity = new PublishLogJpaEntity();
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
        PublishLogJpaEntity saved = logRepo.save(entity);
        return toPublishLogEntry(saved);
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<PublishLogEntry> listPublishLogs(Long draftId) {
        return logRepo.findByDraftIdOrderByCreatedAtDesc(draftId).stream()
                .map(this::toPublishLogEntry)
                .toList();
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param traceId 链路追踪标识
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<PublishLogEntry> listPublishLogsByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return logRepo.findByTraceIdOrderByCreatedAtAsc(traceId).stream()
                .map(this::toPublishLogEntry)
                .toList();
    }

    /**
     * 处理 to domain 相关逻辑，并返回对应的执行结果。
     *
     * @param entity 待处理实体对象
     * @return 方法处理后的结果对象
     */

    private ContentDraft toDomain(ContentDraftJpaEntity entity) {
        return ContentDraft.builder()
                .id(entity.getId())
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

    /**
     * 处理 to domain 相关逻辑，并返回对应的执行结果。
     *
     * @param entity 待处理实体对象
     * @return 方法处理后的结果对象
     */

    private ReviewRecord toDomain(ReviewRecordJpaEntity entity) {
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

    /**
     * 处理 to domain 相关逻辑，并返回对应的执行结果。
     *
     * @param entity 待处理实体对象
     * @return 方法处理后的结果对象
     */

    private ContentSnapshot toDomain(ContentSnapshotJpaEntity entity) {
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

    /**
     * 处理 to command entry 相关逻辑，并返回对应的执行结果。
     *
     * @param entity 待处理实体对象
     * @return 方法处理后的结果对象
     */

    private PublishCommandEntry toCommandEntry(PublishCommandJpaEntity entity) {
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

    /**
     * 处理 to domain 相关逻辑，并返回对应的执行结果。
     *
     * @param entity 待处理实体对象
     * @return 方法处理后的结果对象
     */

    private PublishTask toDomain(PublishTaskJpaEntity entity) {
        return PublishTaskMapper.toDomain(entity);
    }

    /**
     * 处理 to publish log entry 相关逻辑，并返回对应的执行结果。
     *
     * @param entity 待处理实体对象
     * @return 方法处理后的结果对象
     */

    private PublishLogEntry toPublishLogEntry(PublishLogJpaEntity entity) {
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

    /**
     * 构建当前场景所需的结果对象或配置内容。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */
    private Specification<ContentDraftJpaEntity> buildDraftSpec(DraftQueryRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1) Status filter
            if (request.status() != null && !request.status().isEmpty()) {
                predicates.add(root.get("workflowStatus").in(request.status()));
            }

            // 2) Time range filter
            if (request.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.createdFrom()));
            }
            if (request.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.createdTo()));
            }
            if (request.updatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), request.updatedFrom()));
            }
            if (request.updatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("updatedAt"), request.updatedTo()));
            }

            // 3) Keyword filter (bizNo/title/summary/body)
            String keyword = request.keyword();
            boolean hasKeyword = keyword != null && !keyword.isBlank();
            if (hasKeyword) {
                String like = "%" + keyword.toLowerCase() + "%";
                List<Predicate> orPredicates = new ArrayList<>();
                orPredicates.add(cb.like(cb.lower(root.get("bizNo")), like));
                orPredicates.add(cb.like(cb.lower(root.get("title")), like));
                orPredicates.add(cb.like(cb.lower(root.get("summary")), like));
                if (Boolean.TRUE.equals(request.searchInBody())) {
                    orPredicates.add(cb.like(cb.lower(root.get("body")), like));
                }
                predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 处理 to sort 相关逻辑，并返回对应的执行结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */
    private Sort toSort(DraftQueryRequest request) {
        Sort.Direction direction = request.sortDirection() == DraftQueryRequest.SortDirection.DESC
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        String property = switch (request.sortBy()) {
            case ID -> "id";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
        };
        return Sort.by(direction, property).and(Sort.by(direction, "id"));
    }
}

package com.contentworkflow.workflow.application;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.common.web.auth.WorkflowAuditContext;
import com.contentworkflow.common.web.auth.WorkflowAuditContextHolder;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.workflow.application.store.DraftOperationLockEntry;
import com.contentworkflow.workflow.application.store.PublishCommandEntry;
import com.contentworkflow.workflow.application.store.PublishLogEntry;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.application.store.WorkflowAuditLogFactory;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.entity.ReviewRecord;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;
import com.contentworkflow.workflow.domain.enums.PublishChangeScope;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import com.contentworkflow.workflow.interfaces.dto.OfflineRequest;
import com.contentworkflow.workflow.interfaces.dto.PublishRequest;
import com.contentworkflow.workflow.interfaces.dto.ReviewDecisionRequest;
import com.contentworkflow.workflow.interfaces.dto.RollbackRequest;
import com.contentworkflow.workflow.interfaces.dto.SubmitReviewRequest;
import com.contentworkflow.workflow.interfaces.dto.UpdateDraftRequest;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftResponse;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftSummaryResponse;
import com.contentworkflow.workflow.interfaces.vo.ContentSnapshotResponse;
import com.contentworkflow.workflow.interfaces.vo.DraftStatsResponse;
import com.contentworkflow.workflow.interfaces.vo.DraftStatusCountResponse;
import com.contentworkflow.workflow.interfaces.vo.DraftWorkflowSummaryResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishCommandResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishAuditTimelineResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishDiffResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishLogResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishTaskResponse;
import com.contentworkflow.workflow.interfaces.vo.ReviewRecordResponse;
import com.contentworkflow.workflow.interfaces.vo.WorkflowActionResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 应用服务实现，负责组织领域对象、存储组件与外部能力完成业务流程编排。
 */

@Service
public class ContentWorkflowApplicationService implements ContentWorkflowService {

    private final WorkflowStore store;

    private static final String COMMAND_TYPE_PUBLISH = "PUBLISH";
    private static final String CMD_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String CMD_STATUS_ACCEPTED = "ACCEPTED";
    private static final String CMD_STATUS_FAILED = "FAILED";

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */
    public ContentWorkflowApplicationService(WorkflowStore store) {
        this.store = store;
    }

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param store 参数 store 对应的业务输入值
     */


    // Prefer workflow.bootstrap.seedDraft and keep the legacy key as a fallback for compatibility.
    @Value("${workflow.bootstrap.seedDraft:${workflow.demo.seedDraft:false}}")
    private boolean bootstrapSeedDraft;

    @Value("${workflow.draft.operationLockSeconds:1800}")
    private int draftOperationLockSeconds;

    /**
     * 处理 init 相关逻辑，并返回对应的执行结果。
     * Seeds a bootstrap draft for local verification when explicitly enabled.
     */
    @PostConstruct
    public void seedBootstrapDraftIfEnabled() {
        if (bootstrapSeedDraft) {
            createDraft(new CreateDraftRequest(
                    "Course Release Workflow Design",
                    "Bootstrap draft for local workflow verification.",
                    "This draft is inserted at startup to verify the workflow end-to-end in a local environment."
            ), WorkflowOperatorIdentity.system());
        }
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<ContentDraftResponse> listDrafts() {
        return store.listDrafts().stream()
                .map(this::toDraftResponse)
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
    public PageResponse<ContentDraftSummaryResponse> pageDraftSummaries(DraftQueryRequest request) {
        DraftQueryRequest normalized = normalizeQuery(request);
        validateQuery(normalized);
        // Push filtering, pagination, and sorting down to the store layer.
        PageResponse<ContentDraft> page = store.pageDrafts(normalized);
        List<ContentDraftSummaryResponse> items = page.items().stream()
                .map(this::toDraftSummaryResponse)
                .toList();
        return new PageResponse<>(items, page.total(), page.pageNo(), page.pageSize(), page.totalPages());
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional(readOnly = true)
    public DraftWorkflowSummaryResponse getDraftWorkflowSummary(Long draftId) {
        ContentDraft draft = requireDraft(draftId);

        long reviewCount = store.listReviewRecords(draftId).size();
        List<ContentSnapshot> snapshots = store.listSnapshots(draftId);
        long snapshotCount = snapshots.size();
        long publishTaskCount = store.listPublishTasks(draftId).size();
        long publishLogCount = store.listPublishLogs(draftId).size();

        ContentSnapshot latest = snapshots.stream()
                .max(Comparator.comparing(ContentSnapshot::getPublishedVersion))
                .orElse(null);

        return new DraftWorkflowSummaryResponse(
                toDraftSummaryResponse(draft),
                reviewCount,
                snapshotCount,
                publishTaskCount,
                publishLogCount,
                latest == null ? null : latest.getPublishedVersion(),
                latest == null ? null : latest.getPublishedAt()
        );
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional(readOnly = true)
    public DraftStatsResponse getDraftStats(DraftQueryRequest request) {
        DraftQueryRequest normalized = normalizeQuery(request);
        validateQuery(normalized);
        // Push status aggregation down to the store layer.
        Map<WorkflowStatus, Long> counts = store.countDraftsByStatus(normalized);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<DraftStatusCountResponse> byStatus = EnumSet.allOf(WorkflowStatus.class).stream()
                .map(status -> new DraftStatusCountResponse(status, counts.getOrDefault(status, 0L)))
                .toList();

        return new DraftStatsResponse(total, byStatus);
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @param basePublishedVersion 参数 basePublishedVersion 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional(readOnly = true)
    public PublishDiffResponse getPublishDiff(Long draftId, Integer basePublishedVersion) {
        ContentDraft draft = requireDraft(draftId);
        PublishDiffContext diff = loadPublishDiff(draft, basePublishedVersion);

        int currentPublished = safeInt(draft.getPublishedVersion());
        return new PublishDiffResponse(
                draftId,
                safeInt(draft.getDraftVersion()),
                diff.basePublishedVersion(),
                currentPublished,
                currentPublished + 1,
                diff.firstPublish(),
                diff.hasChanges(),
                diff.fields(),
                diff.scopes(),
                diff.plannedTasks()
        );
    }

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse createDraft(CreateDraftRequest request) {
        // Keep legacy callers working by defaulting to the system operator.
        return createDraft(request, WorkflowOperatorIdentity.system());
    }

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse createDraft(CreateDraftRequest request, WorkflowOperatorIdentity operator) {
        LocalDateTime now = LocalDateTime.now();
        ContentDraft draft = ContentDraft.builder()
                .title(request.title())
                .summary(request.summary())
                .body(request.body())
                .draftVersion(1)
                .publishedVersion(0)
                .status(WorkflowStatus.DRAFT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        draft = store.insertDraft(draft);
        store.insertPublishLog(WorkflowAuditLogFactory.operatorAction(draft.getId(), "DRAFT_CREATED", operator)
                .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                .targetId(draft.getId())
                .afterStatus(WorkflowStatus.DRAFT.name())
                .result(WorkflowAuditResult.SUCCESS)
                .remark("draft created")
                .createdAt(now)
                .build());
        return toDraftResponse(draft);
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse updateDraft(Long draftId, UpdateDraftRequest request) {
        return updateDraft(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse updateDraft(Long draftId, UpdateDraftRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        WorkflowStatus beforeStatus = draft.getStatus();
        ensureExpectedVersion(draft, request.expectedVersion());
        ensureState(draft, EnumSet.of(WorkflowStatus.DRAFT, WorkflowStatus.REJECTED, WorkflowStatus.OFFLINE),
                "draft can only be edited in DRAFT, REJECTED or OFFLINE state");
        draft.setTitle(request.title());
        draft.setSummary(request.summary());
        draft.setBody(request.body());
        draft.setDraftVersion(draft.getDraftVersion() + 1);
        draft.setStatus(WorkflowStatus.DRAFT);
        draft.setLastReviewComment(null);
        draft.setUpdatedAt(LocalDateTime.now());
        draft = persistDraft(draft, EnumSet.of(WorkflowStatus.DRAFT, WorkflowStatus.REJECTED, WorkflowStatus.OFFLINE));
        store.insertPublishLog(WorkflowAuditLogFactory.operatorAction(draftId, "DRAFT_UPDATED", operator)
                .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                .targetId(draftId)
                .beforeStatus(beforeStatus.name())
                .afterStatus(WorkflowStatus.DRAFT.name())
                .result(WorkflowAuditResult.SUCCESS)
                .remark("draft updated")
                .createdAt(draft.getUpdatedAt())
                .build());
        return toDraftResponse(draft);
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional(readOnly = true)
    public ContentDraftResponse getDraft(Long draftId) {
        return toDraftResponse(requireDraft(draftId));
    }

    /**
     * 提交当前业务动作，推动流程进入下一处理阶段。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse submitReview(Long draftId, SubmitReviewRequest request) {
        return submitReview(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 提交当前业务动作，推动流程进入下一处理阶段。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse submitReview(Long draftId, SubmitReviewRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        WorkflowStatus beforeStatus = draft.getStatus();
        ensureState(draft, EnumSet.of(WorkflowStatus.DRAFT, WorkflowStatus.REJECTED, WorkflowStatus.OFFLINE),
                "only DRAFT, REJECTED or OFFLINE content can be submitted for review");
        draft.setStatus(WorkflowStatus.REVIEWING);
        draft.setUpdatedAt(LocalDateTime.now());
        draft = persistDraft(draft, EnumSet.of(WorkflowStatus.DRAFT, WorkflowStatus.REJECTED, WorkflowStatus.OFFLINE));
        store.insertPublishLog(WorkflowAuditLogFactory.operatorAction(draftId, "REVIEW_SUBMITTED", operator)
                .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                .targetId(draftId)
                .beforeStatus(beforeStatus.name())
                .afterStatus(WorkflowStatus.REVIEWING.name())
                .result(WorkflowAuditResult.SUCCESS)
                .remark(request.remark())
                .createdAt(draft.getUpdatedAt())
                .build());
        return toDraftResponse(draft);
    }

    /**
     * 执行审核动作，并根据审核结果更新流程状态。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse review(Long draftId, ReviewDecisionRequest request) {
        return review(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 执行审核动作，并根据审核结果更新流程状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse review(Long draftId, ReviewDecisionRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        WorkflowStatus beforeStatus = draft.getStatus();
        ensureState(draft, EnumSet.of(WorkflowStatus.REVIEWING), "draft is not under review");

        LocalDateTime now = LocalDateTime.now();
        ReviewRecord record = ReviewRecord.builder()
                .draftId(draftId)
                .draftVersion(draft.getDraftVersion())
                .reviewer(operator.operatorName())
                .decision(request.decision())
                .comment(request.comment())
                .reviewedAt(now)
                .build();
        store.insertReviewRecord(record);

        if (request.decision() == ReviewDecision.APPROVE) {
            draft.setStatus(WorkflowStatus.APPROVED);
            draft.setLastReviewComment(null);
            store.insertPublishLog(WorkflowAuditLogFactory.operatorAction(draftId, "REVIEW_APPROVED", operator)
                    .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                    .targetId(draftId)
                    .beforeStatus(beforeStatus.name())
                    .afterStatus(WorkflowStatus.APPROVED.name())
                    .result(WorkflowAuditResult.SUCCESS)
                    .remark(request.comment())
                    .createdAt(now)
                    .build());
        } else {
            draft.setStatus(WorkflowStatus.REJECTED);
            draft.setLastReviewComment(request.comment());
            store.insertPublishLog(WorkflowAuditLogFactory.operatorAction(draftId, "REVIEW_REJECTED", operator)
                    .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                    .targetId(draftId)
                    .beforeStatus(beforeStatus.name())
                    .afterStatus(WorkflowStatus.REJECTED.name())
                    .result(WorkflowAuditResult.SUCCESS)
                    .remark(request.comment())
                    .createdAt(now)
                    .build());
        }

        draft.setUpdatedAt(now);
        draft = persistDraft(draft, EnumSet.of(WorkflowStatus.REVIEWING));
        return toDraftResponse(draft);
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse publish(Long draftId, PublishRequest request) {
        return publish(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse publish(Long draftId, PublishRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        WorkflowStatus beforeStatus = draft.getStatus();
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        PublishDiffContext diff = loadPublishDiff(draft, null);
        boolean commandCreated = false;
        boolean enteredPublishing = false;
        Long snapshotId = null;

        if (idempotencyKey != null) {
            // Idempotent replays must not create duplicate snapshots or publish tasks.
            PublishCommandEntry existing = store.findPublishCommand(draftId, COMMAND_TYPE_PUBLISH, idempotencyKey).orElse(null);
            if (existing != null) {
                if (CMD_STATUS_FAILED.equals(existing.getStatus())) {
                    throw new BusinessException("PUBLISH_COMMAND_FAILED",
                            "previous publish command failed: " + safeString(existing.getErrorMessage()));
                }
                // The same idempotency key may only replay the same draft payload.
                ensureIdempotencyKeyNotReusedForDifferentPayload(draft, existing);
                // For IN_PROGRESS / ACCEPTED, return the current draft state without duplicating side effects.
                return toDraftResponse(draft);
            }
        }

        ensureState(draft, EnumSet.of(WorkflowStatus.APPROVED, WorkflowStatus.PUBLISH_FAILED),
                "only APPROVED or PUBLISH_FAILED content can be published");
        ensurePublishHasChanges(diff);

        LocalDateTime now = LocalDateTime.now();
        int targetPublishedVersion = safeInt(draft.getPublishedVersion()) + 1;
        acquireDraftOperationLock(draftId, DraftOperationType.PUBLISH, targetPublishedVersion, operator.operatorName(), now);
        boolean tasksCreated = false;
        try {
            if (idempotencyKey != null) {
                boolean created = store.tryCreatePublishCommand(PublishCommandEntry.builder()
                        .draftId(draftId)
                        .commandType(COMMAND_TYPE_PUBLISH)
                        .idempotencyKey(idempotencyKey)
                        .operatorName(operator.operatorName())
                        .remark(request.remark())
                        .status(CMD_STATUS_IN_PROGRESS)
                        .targetPublishedVersion(targetPublishedVersion)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                if (!created) {
                    // Concurrent insertion may have won the race; fall back to the existing command record.
                    PublishCommandEntry cmd = store.findPublishCommand(draftId, COMMAND_TYPE_PUBLISH, idempotencyKey).orElse(null);
                    if (cmd != null && CMD_STATUS_FAILED.equals(cmd.getStatus())) {
                        throw new BusinessException("PUBLISH_COMMAND_FAILED",
                                "previous publish command failed: " + safeString(cmd.getErrorMessage()));
                    }
                    if (cmd != null) {
                        ensureIdempotencyKeyNotReusedForDifferentPayload(draft, cmd);
                    }
                    releaseDraftOperationLockQuietly(draftId, targetPublishedVersion);
                    return toDraftResponse(draft);
                }
                commandCreated = true;
            }

            draft.setStatus(WorkflowStatus.PUBLISHING);
            draft.setUpdatedAt(now);
            draft = persistDraft(draft, EnumSet.of(WorkflowStatus.APPROVED, WorkflowStatus.PUBLISH_FAILED));
            enteredPublishing = true;

            int nextPublishedVersion = targetPublishedVersion;
            ContentSnapshot snapshot = ContentSnapshot.builder()
                    .draftId(draftId)
                    .publishedVersion(nextPublishedVersion)
                    .sourceDraftVersion(draft.getDraftVersion())
                    .title(draft.getTitle())
                    .summary(draft.getSummary())
                    .body(draft.getBody())
                    .operator(operator.operatorName())
                    .rollback(false)
                    .publishedAt(now)
                    .build();
            snapshot = store.insertSnapshot(snapshot);
            snapshotId = snapshot.getId();

            EnumSet<PublishTaskType> taskTypes = resolvePublishTaskTypes(diff);
            List<PublishTask> tasks = new ArrayList<>(taskTypes.size());
            for (PublishTaskType taskType : taskTypes) {
                tasks.add(newPublishTask(draftId, nextPublishedVersion, taskType, now));
            }
            store.insertPublishTasks(tasks);
            tasksCreated = true;

            draft.setPublishedVersion(nextPublishedVersion);
            draft.setCurrentSnapshotId(snapshot.getId());
            // Final completion depends on async task execution: SUCCESS => PUBLISHED, unrecoverable failure => PUBLISH_FAILED.
            draft.setUpdatedAt(now);
            draft = persistDraft(draft, EnumSet.of(WorkflowStatus.PUBLISHING));

            if (idempotencyKey != null) {
                store.updatePublishCommand(PublishCommandEntry.builder()
                        .draftId(draftId)
                        .commandType(COMMAND_TYPE_PUBLISH)
                        .idempotencyKey(idempotencyKey)
                        .operatorName(operator.operatorName())
                        .remark(request.remark())
                        .status(CMD_STATUS_ACCEPTED)
                        .targetPublishedVersion(nextPublishedVersion)
                        .snapshotId(snapshot.getId())
                        .errorMessage(null)
                        .updatedAt(now)
                        .build());
            }

            store.insertPublishLog(WorkflowAuditLogFactory.publishAction(draftId, nextPublishedVersion, "PUBLISH_REQUESTED", operator)
                    .beforeStatus(beforeStatus.name())
                    .afterStatus(WorkflowStatus.PUBLISHING.name())
                    .result(WorkflowAuditResult.ACCEPTED)
                    .remark(buildPublishAuditRemark(request.remark(), diff))
                    // Change scopes/tasks are recorded in the publish audit remark for traceability.
                    .createdAt(now)
                    .build());
            return toDraftResponse(draft);
        } catch (RuntimeException e) {
            LocalDateTime failAt = LocalDateTime.now();
            if (enteredPublishing) {
                draft = transitionToPublishFailed(
                        draftId,
                        targetPublishedVersion,
                        operator,
                        draft,
                        failAt,
                        e.getMessage(),
                        "PUBLISH_REQUEST_FAILED"
                );
            }
            if (!tasksCreated) {
                releaseDraftOperationLockQuietly(draftId, targetPublishedVersion);
            }

            if (commandCreated) {
                updatePublishCommandAsFailed(
                        draftId,
                        idempotencyKey,
                        operator,
                        request.remark(),
                        targetPublishedVersion,
                        snapshotId != null ? snapshotId : draft.getCurrentSnapshotId(),
                        e.getMessage(),
                        failAt
                );
            }
            throw e;
        }
    }

    /**
     * 执行回滚流程，将业务状态恢复到目标版本或阶段。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse rollback(Long draftId, RollbackRequest request) {
        return rollback(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 执行回滚流程，将业务状态恢复到目标版本或阶段。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    @Override
    @Transactional
    public ContentDraftResponse rollback(Long draftId, RollbackRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        WorkflowStatus beforeStatus = draft.getStatus();
        ensureState(draft, EnumSet.of(WorkflowStatus.PUBLISHED, WorkflowStatus.OFFLINE),
                "only PUBLISHED or OFFLINE content can rollback");

        List<ContentSnapshot> snapshotList = store.listSnapshots(draftId);
        ContentSnapshot target = snapshotList.stream()
                .filter(snapshot -> snapshot.getPublishedVersion().equals(request.targetPublishedVersion()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("SNAPSHOT_NOT_FOUND", "target published version not found"));

        LocalDateTime now = LocalDateTime.now();
        int nextPublishedVersion = draft.getPublishedVersion() + 1;
        acquireDraftOperationLock(draftId, DraftOperationType.ROLLBACK, nextPublishedVersion, operator.operatorName(), now);
        boolean enteredPublishing = false;
        boolean tasksCreated = false;

        try {
            draft.setStatus(WorkflowStatus.PUBLISHING);
            draft.setUpdatedAt(now);
            draft = persistDraft(draft, EnumSet.of(WorkflowStatus.PUBLISHED, WorkflowStatus.OFFLINE));
            enteredPublishing = true;

            ContentSnapshot rollbackSnapshot = ContentSnapshot.builder()
                    .draftId(draftId)
                    .publishedVersion(nextPublishedVersion)
                    .sourceDraftVersion(draft.getDraftVersion())
                    .title(target.getTitle())
                    .summary(target.getSummary())
                    .body(target.getBody())
                    .operator(operator.operatorName())
                    .rollback(true)
                    .publishedAt(now)
                    .build();
            rollbackSnapshot = store.insertSnapshot(rollbackSnapshot);

            List<PublishTask> tasks = new ArrayList<>();
            for (PublishTaskType taskType : PublishTaskType.values()) {
                tasks.add(newPublishTask(draftId, nextPublishedVersion, taskType, now));
            }
            store.insertPublishTasks(tasks);
            tasksCreated = true;

            draft.setTitle(target.getTitle());
            draft.setSummary(target.getSummary());
            draft.setBody(target.getBody());
            draft.setPublishedVersion(nextPublishedVersion);
            draft.setCurrentSnapshotId(rollbackSnapshot.getId());
            draft.setStatus(WorkflowStatus.PUBLISHING);
            // Rollback reuses publish task orchestration and returns to PUBLISHING until tasks complete.
            draft.setUpdatedAt(now);
            draft = persistDraft(draft, EnumSet.of(WorkflowStatus.PUBLISHING));

            store.insertPublishLog(WorkflowAuditLogFactory.publishAction(draftId, nextPublishedVersion, "ROLLBACK_REQUESTED", operator)
                    .beforeStatus(beforeStatus.name())
                    .afterStatus(WorkflowStatus.PUBLISHING.name())
                    .result(WorkflowAuditResult.ACCEPTED)
                    .remark(request.reason())
                    .createdAt(now)
                    .build());
            return toDraftResponse(draft);
        } catch (RuntimeException e) {
            if (!tasksCreated) {
                releaseDraftOperationLockQuietly(draftId, nextPublishedVersion);
            }
            if (enteredPublishing) {
                transitionToPublishFailed(
                        draftId,
                        nextPublishedVersion,
                        operator,
                        draft,
                        LocalDateTime.now(),
                        e.getMessage(),
                        "ROLLBACK_REQUEST_FAILED"
                );
            }
            throw e;
        }
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<ReviewRecordResponse> listReviews(Long draftId) {
        requireDraft(draftId);
        return store.listReviewRecords(draftId).stream()
                .sorted(Comparator.comparing(ReviewRecord::getReviewedAt).reversed())
                .map(record -> new ReviewRecordResponse(
                        record.getId(),
                        record.getDraftVersion(),
                        record.getReviewer(),
                        record.getDecision(),
                        record.getComment(),
                        record.getReviewedAt()))
                .toList();
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    @Transactional(readOnly = true)
    public List<ContentSnapshotResponse> listSnapshots(Long draftId) {
        requireDraft(draftId);
        return store.listSnapshots(draftId).stream()
                .sorted(Comparator.comparing(ContentSnapshot::getPublishedVersion).reversed())
                .map(snapshot -> new ContentSnapshotResponse(
                        snapshot.getId(),
                        snapshot.getPublishedVersion(),
                        snapshot.getSourceDraftVersion(),
                        snapshot.getTitle(),
                        snapshot.getSummary(),
                        snapshot.getBody(),
                        snapshot.getOperator(),
                        snapshot.isRollback(),
                        snapshot.getPublishedAt()))
                .toList();
    }

    /**
     * 处理 require draft 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @return 方法处理后的结果对象
     */

    private ContentDraft requireDraft(Long draftId) {
        return store.findDraftById(draftId)
                .orElseThrow(() -> new BusinessException("DRAFT_NOT_FOUND", "draft not found"));
    }

    /**
     * 处理 ensure state 相关逻辑，并返回对应的执行结果。
     *
     * @param draft 草稿对象
     * @param allowedStates 参数 allowedStates 对应的业务输入值
     * @param message 提示信息
     */

private void ensureState(ContentDraft draft, EnumSet<WorkflowStatus> allowedStates, String message) {
    if (!allowedStates.contains(draft.getStatus())) {
        throw new BusinessException("INVALID_WORKFLOW_STATE", message + ", current state: " + draft.getStatus());
    }
}

private void ensureExpectedVersion(ContentDraft draft, Long expectedVersion) {
    if (expectedVersion == null) {
        throw new BusinessException("VERSION_REQUIRED", "expectedVersion is required for draft updates");
    }
    if (!Objects.equals(draft.getVersion(), expectedVersion)) {
        throw new BusinessException(
                "CONCURRENT_MODIFICATION",
                "draft changed concurrently, expectedVersion=" + expectedVersion + ", actualVersion=" + draft.getVersion()
        );
    }
}

private ContentDraft persistDraft(ContentDraft draft, EnumSet<WorkflowStatus> expectedStatuses) {
    return store.updateDraft(draft, expectedStatuses);
}

private void acquireDraftOperationLock(Long draftId,
                                       DraftOperationType operationType,
                                       Integer targetPublishedVersion,
                                       String lockedBy,
                                       LocalDateTime now) {
    DraftOperationLockEntry lockEntry = DraftOperationLockEntry.builder()
            .draftId(draftId)
            .operationType(operationType)
            .targetPublishedVersion(targetPublishedVersion)
            .lockedBy(lockedBy == null || lockedBy.isBlank() ? "system" : lockedBy)
            .lockedAt(now)
            .expiresAt(now.plusSeconds(Math.max(60, draftOperationLockSeconds)))
            .build();
    if (store.tryAcquireDraftOperationLock(lockEntry, now)) {
        return;
    }
    DraftOperationLockEntry current = store.findDraftOperationLock(draftId).orElse(null);
    throw draftOperationInProgress(draftId, current);
}

private void releaseDraftOperationLockQuietly(Long draftId, Integer targetPublishedVersion) {
    try {
        store.releaseDraftOperationLock(draftId, targetPublishedVersion);
    } catch (RuntimeException ignored) {
        // Best-effort release; the lease expiry remains as a safety net.
    }
}

private BusinessException draftOperationInProgress(Long draftId, DraftOperationLockEntry current) {
    String detail = current == null
            ? "another draft operation is already in progress"
            : "operationType=" + current.getOperationType()
            + ", targetPublishedVersion=" + current.getTargetPublishedVersion()
            + ", lockedBy=" + current.getLockedBy()
            + ", expiresAt=" + current.getExpiresAt();
    return new BusinessException(
            "DRAFT_OPERATION_IN_PROGRESS",
            "draft operation is locked, draftId=" + draftId + ", " + detail
    );
}

private ContentDraft transitionToPublishFailed(Long draftId,
                                               Integer publishedVersion,
                                               WorkflowOperatorIdentity operator,
                                               ContentDraft draft,
                                               LocalDateTime failedAt,
                                               String errorMessage,
                                               String errorCode) {
    draft.setStatus(WorkflowStatus.PUBLISH_FAILED);
    draft.setUpdatedAt(failedAt);
    try {
        ContentDraft failedDraft = persistDraft(draft, EnumSet.of(WorkflowStatus.PUBLISHING));
        store.insertPublishLog(WorkflowAuditLogFactory.publishAction(draftId, publishedVersion, "PUBLISH_FAILED", operator)
                .beforeStatus(WorkflowStatus.PUBLISHING.name())
                .afterStatus(WorkflowStatus.PUBLISH_FAILED.name())
                .result(WorkflowAuditResult.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .remark(errorMessage)
                .createdAt(failedAt)
                .build());
        return failedDraft;
    } catch (BusinessException ex) {
        if (!isConditionalTransitionConflict(ex)) {
            throw ex;
        }
        return draft;
    }
}

private boolean isConditionalTransitionConflict(BusinessException ex) {
    return "CONCURRENT_MODIFICATION".equals(ex.getCode())
            || "INVALID_WORKFLOW_STATE".equals(ex.getCode());
}

private void updatePublishCommandAsFailed(Long draftId,
                                          String idempotencyKey,
                                          WorkflowOperatorIdentity operator,
                                          String remark,
                                          Integer targetPublishedVersion,
                                          Long snapshotId,
                                          String errorMessage,
                                          LocalDateTime failedAt) {
    store.updatePublishCommand(PublishCommandEntry.builder()
            .draftId(draftId)
            .commandType(COMMAND_TYPE_PUBLISH)
            .idempotencyKey(idempotencyKey)
            .operatorName(operator.operatorName())
            .remark(remark)
            .status(CMD_STATUS_FAILED)
            .targetPublishedVersion(targetPublishedVersion)
            .snapshotId(snapshotId)
            .errorMessage(errorMessage)
            .updatedAt(failedAt)
            .build());
}

    /**
     * 处理 to draft response 相关逻辑，并返回对应的执行结果。
     *
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

    private ContentDraftResponse toDraftResponse(ContentDraft draft) {
        return new ContentDraftResponse(
                draft.getId(),
                draft.getBizNo(),
                draft.getTitle(),
                draft.getSummary(),
                draft.getBody(),
                draft.getDraftVersion(),
                draft.getPublishedVersion(),
                draft.getVersion(),
                draft.getStatus(),
                draft.getCurrentSnapshotId(),
                draft.getLastReviewComment(),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }

    /**
     * 处理 to draft summary response 相关逻辑，并返回对应的执行结果。
     *
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

    private ContentDraftSummaryResponse toDraftSummaryResponse(ContentDraft draft) {
        return new ContentDraftSummaryResponse(
                draft.getId(),
                draft.getBizNo(),
                draft.getTitle(),
                draft.getSummary(),
                draft.getDraftVersion(),
                draft.getPublishedVersion(),
                draft.getVersion(),
                draft.getStatus(),
                draft.getCurrentSnapshotId(),
                draft.getLastReviewComment(),
                draft.getCreatedAt(),
                draft.getUpdatedAt(),
                toActions(draft.getStatus())
        );
    }

    /**
     * 处理 to actions 相关逻辑，并返回对应的执行结果。
     *
     * @param status 状态值
     * @return 方法处理后的结果对象
     */
    private WorkflowActionResponse toActions(WorkflowStatus status) {
        boolean canEdit = status == WorkflowStatus.DRAFT
                || status == WorkflowStatus.REJECTED
                || status == WorkflowStatus.OFFLINE;
        boolean canSubmitReview = canEdit;
        boolean canReview = status == WorkflowStatus.REVIEWING;
        boolean canPublish = status == WorkflowStatus.APPROVED || status == WorkflowStatus.PUBLISH_FAILED;
        boolean canRollback = status == WorkflowStatus.PUBLISHED || status == WorkflowStatus.OFFLINE;
        boolean canOffline = status == WorkflowStatus.PUBLISHED;
        return new WorkflowActionResponse(canEdit, canSubmitReview, canReview, canPublish, canRollback, canOffline);
    }

    /**
     * 对输入值进行标准化处理，便于后续统一使用。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    private DraftQueryRequest normalizeQuery(DraftQueryRequest request) {
        if (request == null) {
            return new DraftQueryRequest(null, null, false, 1, 20,
                    DraftQueryRequest.DraftSortBy.UPDATED_AT, DraftQueryRequest.SortDirection.DESC,
                    null, null, null, null).normalized();
        }
        return request.normalized();
    }

    /**
     * 处理 validate query 相关逻辑，并返回对应的执行结果。
     *
     * @param request 封装业务输入的请求对象
     */

    private void validateQuery(DraftQueryRequest request) {
        // Use business errors instead of IllegalArgumentException to avoid surfacing as HTTP 500.
        if (request.createdFrom() != null && request.createdTo() != null && request.createdFrom().isAfter(request.createdTo())) {
            throw new BusinessException("INVALID_QUERY", "createdFrom must be <= createdTo");
        }
        if (request.updatedFrom() != null && request.updatedTo() != null && request.updatedFrom().isAfter(request.updatedTo())) {
            throw new BusinessException("INVALID_QUERY", "updatedFrom must be <= updatedTo");
        }
    }

    /**
     * 处理 filter and sort drafts 相关逻辑，并返回对应的执行结果。
     *
     * @param drafts 参数 drafts 对应的业务输入值
     * @param request 封装业务输入的请求对象
     * @return 符合条件的结果集合
     */

    private List<ContentDraft> filterAndSortDrafts(List<ContentDraft> drafts, DraftQueryRequest request) {
        return sortDrafts(filterDrafts(drafts, request), request);
    }

    /**
     * 处理 filter drafts 相关逻辑，并返回对应的执行结果。
     *
     * @param drafts 参数 drafts 对应的业务输入值
     * @param request 封装业务输入的请求对象
     * @return 符合条件的结果集合
     */
    private List<ContentDraft> filterDrafts(List<ContentDraft> drafts, DraftQueryRequest request) {
        // normalizeQuery() has already trimmed keyword input.
        String keyword = request.keyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String keywordLowerCase = hasKeyword ? keyword.toLowerCase() : null;
        boolean searchInBody = request.searchInBody() != null && request.searchInBody();
        List<WorkflowStatus> statuses = request.status();

        LocalDateTime createdFrom = request.createdFrom();
        LocalDateTime createdTo = request.createdTo();
        LocalDateTime updatedFrom = request.updatedFrom();
        LocalDateTime updatedTo = request.updatedTo();

        List<ContentDraft> filtered = drafts.stream()
                .filter(d -> statuses == null || statuses.isEmpty() || statuses.contains(d.getStatus()))
                .filter(d -> {
                    if (!hasKeyword) {
                        return true;
                    }
                    if (containsIgnoreCase(d.getBizNo(), keywordLowerCase)) return true;
                    if (containsIgnoreCase(d.getTitle(), keywordLowerCase)) return true;
                    if (containsIgnoreCase(d.getSummary(), keywordLowerCase)) return true;
                    return searchInBody && containsIgnoreCase(d.getBody(), keywordLowerCase);
                })
                .filter(d -> createdFrom == null || (d.getCreatedAt() != null && !d.getCreatedAt().isBefore(createdFrom)))
                .filter(d -> createdTo == null || (d.getCreatedAt() != null && !d.getCreatedAt().isAfter(createdTo)))
                .filter(d -> updatedFrom == null || (d.getUpdatedAt() != null && !d.getUpdatedAt().isBefore(updatedFrom)))
                .filter(d -> updatedTo == null || (d.getUpdatedAt() != null && !d.getUpdatedAt().isAfter(updatedTo)))
                .toList();
        return filtered;
    }

    /**
     * 处理 sort drafts 相关逻辑，并返回对应的执行结果。
     *
     * @param drafts 参数 drafts 对应的业务输入值
     * @param request 封装业务输入的请求对象
     * @return 符合条件的结果集合
     */
    private List<ContentDraft> sortDrafts(List<ContentDraft> drafts, DraftQueryRequest request) {
        Comparator<ContentDraft> comparator = switch (request.sortBy()) {
            case ID -> Comparator.comparing(ContentDraft::getId, Comparator.nullsLast(Long::compareTo));
            case CREATED_AT -> Comparator.comparing(ContentDraft::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            case UPDATED_AT -> Comparator.comparing(ContentDraft::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
        };
        comparator = comparator.thenComparing(ContentDraft::getId, Comparator.nullsLast(Long::compareTo));
        if (request.sortDirection() == DraftQueryRequest.SortDirection.DESC) {
            comparator = comparator.reversed();
        }

        return drafts.stream().sorted(comparator).toList();
    }

/**
 * 处理 contains ignore case 相关逻辑，并返回对应的执行结果。
 *
 * @param text 参数 text 对应的业务输入值
 * @param keywordLowerCase 参数 keywordLowerCase 对应的业务输入值
 * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
 */

private boolean containsIgnoreCase(String text, String keywordLowerCase) {
    if (text == null) {
        return false;
    }
    return text.toLowerCase().contains(keywordLowerCase);
}

/**
 * 处理 ensure publish has changes 相关逻辑，并返回对应的执行结果。
 *
 * @param diff 参数 diff 对应的业务输入值
 */

private void ensurePublishHasChanges(PublishDiffContext diff) {
    if (!diff.firstPublish() && !diff.hasChanges()) {
        throw new BusinessException("NO_PUBLISH_CHANGES", "publish rejected because no content changes were detected");
    }
}

/**
 * 解析输入信息并生成当前流程所需的结构化结果。
 *
 * @param diff 参数 diff 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private EnumSet<PublishTaskType> resolvePublishTaskTypes(PublishDiffContext diff) {
    return diff.plannedTaskTypes();
}

/**
 * 处理 ensure idempotency key not reused for different payload 相关逻辑，并返回对应的执行结果。
 *
 * @param draft 草稿对象
 * @param existing 参数 existing 对应的业务输入值
 */
private void ensureIdempotencyKeyNotReusedForDifferentPayload(ContentDraft draft, PublishCommandEntry existing) {
    if (existing == null || existing.getSnapshotId() == null) {
        return;
    }
    ContentSnapshot snapshot = store.listSnapshots(draft.getId()).stream()
            .filter(item -> Objects.equals(item.getId(), existing.getSnapshotId()))
            .findFirst()
            .orElse(null);
    if (snapshot == null) {
        // Tolerate missing historical snapshot data instead of blocking an idempotent replay.
        return;
    }
    boolean same = Objects.equals(snapshot.getTitle(), draft.getTitle())
            && Objects.equals(snapshot.getSummary(), draft.getSummary())
            && Objects.equals(snapshot.getBody(), draft.getBody());
    if (!same) {
        throw new BusinessException("IDEMPOTENCY_KEY_REUSED",
                "idempotencyKey already used for a different draft payload; generate a new key");
    }
}

/**
 * 构建当前场景所需的结果对象或配置内容。
 *
 * @param remark 参数 remark 对应的业务输入值
 * @param diff 参数 diff 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private String buildPublishAuditRemark(String remark, PublishDiffContext diff) {
    String base = remark == null ? "" : remark.trim();
    String scopes = diff.scopes().stream()
            .map(s -> s.scope().name())
            .distinct()
            .sorted()
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    String tasks = diff.plannedTasks().stream()
            .map(t -> t.taskType().name())
            .distinct()
            .sorted()
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    String suffix = "scopes=" + scopes + ";tasks=" + tasks;
    if (base.isEmpty()) {
        return suffix;
    }
    return base + " | " + suffix;
}

/**
 * 加载当前流程所需的数据内容。
 *
 * @param draft 草稿对象
 * @param requestedBasePublishedVersion 参数 requestedBasePublishedVersion 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private PublishDiffContext loadPublishDiff(ContentDraft draft, Integer requestedBasePublishedVersion) {
    int baseVersion = requestedBasePublishedVersion == null
            ? resolveDefaultBasePublishedVersion(draft)
            : requestedBasePublishedVersion;
    if (baseVersion < 0) {
        throw new BusinessException("INVALID_ARGUMENT", "basePublishedVersion must be >= 0");
    }

    ContentSnapshot baseSnapshot = null;
    if (baseVersion > 0) {
        baseSnapshot = store.listSnapshots(draft.getId()).stream()
                .filter(snapshot -> Objects.equals(snapshot.getPublishedVersion(), baseVersion))
                .findFirst()
                .orElseThrow(() -> new BusinessException("SNAPSHOT_NOT_FOUND", "base published version not found"));
    }

    PublishDiffResponse.FieldDiff title = buildFieldDiff(
            "title",
            baseSnapshot == null ? null : baseSnapshot.getTitle(),
            draft.getTitle(),
            200
    );
    PublishDiffResponse.FieldDiff summary = buildFieldDiff(
            "summary",
            baseSnapshot == null ? null : baseSnapshot.getSummary(),
            draft.getSummary(),
            200
    );
    PublishDiffResponse.FieldDiff body = buildFieldDiff(
            "body",
            baseSnapshot == null ? null : baseSnapshot.getBody(),
            draft.getBody(),
            200
    );

    List<PublishDiffResponse.FieldDiff> fields = List.of(title, summary, body);
    BodyDiffAnalysis bodyAnalysis = analyzeBody(
            baseSnapshot == null ? null : baseSnapshot.getBody(),
            draft.getBody()
    );

    boolean titleChanged = title.changed();
    boolean summaryChanged = summary.changed();
    boolean firstPublish = baseVersion == 0;
    boolean hasChanges = fields.stream().anyMatch(PublishDiffResponse.FieldDiff::changed);

    List<PublishDiffResponse.ChangeScopeSummary> scopes = buildChangeScopes(
            firstPublish,
            titleChanged,
            summaryChanged,
            bodyAnalysis
    );
    List<PublishDiffResponse.PlannedTask> plannedTasks = buildPlannedTasks(firstPublish, scopes);
    EnumSet<PublishTaskType> taskTypes = plannedTasks.stream()
            .map(PublishDiffResponse.PlannedTask::taskType)
            .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(PublishTaskType.class)));

    return new PublishDiffContext(baseVersion, firstPublish, hasChanges, fields, scopes, plannedTasks, taskTypes);
}

/**
 * 解析输入信息并生成当前流程所需的结构化结果。
 *
 * @param draft 草稿对象
 * @return 统计值或数量结果
 */

private int resolveDefaultBasePublishedVersion(ContentDraft draft) {
    int publishedVersion = safeInt(draft.getPublishedVersion());
    if (draft.getStatus() == WorkflowStatus.PUBLISH_FAILED && publishedVersion > 0) {
        // Retry from PUBLISH_FAILED should compare with the last stable version, not the failed target version.
        return publishedVersion - 1;
    }
    return publishedVersion;
}

/**
 * 处理 safe int 相关逻辑，并返回对应的执行结果。
 *
 * @param value 待处理的原始值
 * @return 统计值或数量结果
 */

private int safeInt(Integer value) {
    return value == null ? 0 : value;
}

/**
 * 处理 safe string 相关逻辑，并返回对应的执行结果。
 *
 * @param value 待处理的原始值
 * @return 方法处理后的结果对象
 */

private String safeString(String value) {
    return value == null ? "" : value;
}

/**
 * 对输入值进行标准化处理，便于后续统一使用。
 *
 * @param key 缓存或业务标识键
 * @return 方法处理后的结果对象
 */

private String normalizeIdempotencyKey(String key) {
    if (key == null) {
        return null;
    }
    String trimmed = key.trim();
    return trimmed.isEmpty() ? null : trimmed;
}

/**
 * 构建当前场景所需的结果对象或配置内容。
 *
 * @param field 参数 field 对应的业务输入值
 * @param before 参数 before 对应的业务输入值
 * @param after 参数 after 对应的业务输入值
 * @param previewLimit 参数 previewLimit 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private PublishDiffResponse.FieldDiff buildFieldDiff(String field, String before, String after, int previewLimit) {
    boolean changed = !Objects.equals(before, after);
    return new PublishDiffResponse.FieldDiff(
            field,
            changed,
            preview(before, previewLimit),
            preview(after, previewLimit),
            sha256Hex(before),
            sha256Hex(after)
    );
}

/**
 * 处理 preview 相关逻辑，并返回对应的执行结果。
 *
 * @param text 参数 text 对应的业务输入值
 * @param maxLen 参数 maxLen 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private String preview(String text, int maxLen) {
    if (text == null) {
        return null;
    }
    if (maxLen <= 0) {
        return "";
    }
    if (text.length() <= maxLen) {
        return text;
    }
    return text.substring(0, maxLen);
}

/**
 * 处理 sha256 hex 相关逻辑，并返回对应的执行结果。
 *
 * @param text 参数 text 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private String sha256Hex(String text) {
    if (text == null) {
        return null;
    }
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    } catch (Exception e) {
        // Should never happen for SHA-256, but keep API stable.
        return null;
    }
}

/**
 * 构建当前场景所需的结果对象或配置内容。
 *
 * @param firstPublish 参数 firstPublish 对应的业务输入值
 * @param titleChanged 参数 titleChanged 对应的业务输入值
 * @param summaryChanged 参数 summaryChanged 对应的业务输入值
 * @param bodyAnalysis 参数 bodyAnalysis 对应的业务输入值
 * @return 符合条件的结果集合
 */

private List<PublishDiffResponse.ChangeScopeSummary> buildChangeScopes(boolean firstPublish,
                                                                       boolean titleChanged,
                                                                       boolean summaryChanged,
                                                                       BodyDiffAnalysis bodyAnalysis) {
    List<PublishDiffResponse.ChangeScopeSummary> scopes = new ArrayList<>();
    if (titleChanged || summaryChanged) {
        scopes.add(new PublishDiffResponse.ChangeScopeSummary(
                PublishChangeScope.METADATA,
                true,
                firstPublish
                        ? "first publish includes metadata initialization"
                        : "title or summary changed"
        ));
    }
    if (bodyAnalysis.contentChanged()) {
        scopes.add(new PublishDiffResponse.ChangeScopeSummary(
                PublishChangeScope.BODY_CONTENT,
                true,
                firstPublish
                        ? "first publish includes body content"
                        : "normalized body content changed"
        ));
    }
    if (bodyAnalysis.structureChanged()) {
        scopes.add(new PublishDiffResponse.ChangeScopeSummary(
                PublishChangeScope.BODY_STRUCTURE,
                true,
                firstPublish
                        ? "first publish includes body structure"
                        : "body structure changed"
        ));
    }
    if (bodyAnalysis.formatOnlyChanged()) {
        scopes.add(new PublishDiffResponse.ChangeScopeSummary(
                PublishChangeScope.FORMAT_ONLY,
                true,
                "formatting changed without semantic text changes"
        ));
    }
    return scopes;
}

/**
 * 构建当前场景所需的结果对象或配置内容。
 *
 * @param firstPublish 参数 firstPublish 对应的业务输入值
 * @param scopes 参数 scopes 对应的业务输入值
 * @return 符合条件的结果集合
 */

private List<PublishDiffResponse.PlannedTask> buildPlannedTasks(boolean firstPublish,
                                                                List<PublishDiffResponse.ChangeScopeSummary> scopes) {
    List<PublishDiffResponse.PlannedTask> tasks = new ArrayList<>();
    if (firstPublish) {
        tasks.add(new PublishDiffResponse.PlannedTask(
                PublishTaskType.REFRESH_SEARCH_INDEX,
                "first publish requires full search index refresh"
        ));
        tasks.add(new PublishDiffResponse.PlannedTask(
                PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL,
                "first publish requires downstream read model sync"
        ));
        tasks.add(new PublishDiffResponse.PlannedTask(
                PublishTaskType.SEND_PUBLISH_NOTIFICATION,
                "first publish sends initial publish notification"
        ));
        return tasks;
    }

    boolean metadataChanged = hasScope(scopes, PublishChangeScope.METADATA);
    boolean bodyContentChanged = hasScope(scopes, PublishChangeScope.BODY_CONTENT);
    boolean bodyStructureChanged = hasScope(scopes, PublishChangeScope.BODY_STRUCTURE);
    boolean formatOnlyChanged = hasScope(scopes, PublishChangeScope.FORMAT_ONLY);

    if (metadataChanged || bodyContentChanged || bodyStructureChanged) {
        tasks.add(new PublishDiffResponse.PlannedTask(
                PublishTaskType.REFRESH_SEARCH_INDEX,
                metadataChanged
                        ? "metadata affects searchable title/summary"
                        : "body content or structure affects indexable content"
        ));
    }
    if (metadataChanged || bodyContentChanged || bodyStructureChanged || formatOnlyChanged) {
        tasks.add(new PublishDiffResponse.PlannedTask(
                PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL,
                formatOnlyChanged && !metadataChanged && !bodyContentChanged && !bodyStructureChanged
                        ? "format-only change still requires read model refresh"
                        : "published view must be rebuilt from changed content"
        ));
    }
    if (metadataChanged) {
        tasks.add(new PublishDiffResponse.PlannedTask(
                PublishTaskType.SEND_PUBLISH_NOTIFICATION,
                "title or summary changed and may affect subscribers"
        ));
    }
    return tasks;
}

/**
 * 判断当前条件下是否满足指定约束或权限要求。
 *
 * @param scopes 参数 scopes 对应的业务输入值
 * @param scope 参数 scope 对应的业务输入值
 * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
 */

private boolean hasScope(List<PublishDiffResponse.ChangeScopeSummary> scopes, PublishChangeScope scope) {
    return scopes.stream().anyMatch(item -> item.scope() == scope && item.changed());
}

/**
 * 处理 analyze body 相关逻辑，并返回对应的执行结果。
 *
 * @param before 参数 before 对应的业务输入值
 * @param after 参数 after 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private BodyDiffAnalysis analyzeBody(String before, String after) {
    boolean rawChanged = !Objects.equals(before, after);
    if (!rawChanged) {
        return new BodyDiffAnalysis(false, false, false);
    }

    String normalizedBefore = normalizeWhitespace(before);
    String normalizedAfter = normalizeWhitespace(after);
    boolean contentChanged = !Objects.equals(normalizedBefore, normalizedAfter);

    BodyStructure beforeStructure = analyzeStructure(before);
    BodyStructure afterStructure = analyzeStructure(after);
    boolean structureChanged = !Objects.equals(beforeStructure, afterStructure);

    boolean formatOnlyChanged = rawChanged && !contentChanged && !structureChanged;
    return new BodyDiffAnalysis(contentChanged, structureChanged, formatOnlyChanged);
}

/**
 * 对输入值进行标准化处理，便于后续统一使用。
 *
 * @param text 参数 text 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private String normalizeWhitespace(String text) {
    if (text == null) {
        return null;
    }
    return text.trim().replaceAll("\\s+", " ");
}

/**
 * 处理 analyze structure 相关逻辑，并返回对应的执行结果。
 *
 * @param text 参数 text 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private BodyStructure analyzeStructure(String text) {
    if (text == null || text.isBlank()) {
        return new BodyStructure(0, 0, 0);
    }
    String[] lines = text.split("\\R");
    int paragraphCount = 0;
    int headingCount = 0;
    int listItemCount = 0;
    boolean inParagraph = false;

    for (String line : lines) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            inParagraph = false;
            continue;
        }
        if (!inParagraph) {
            paragraphCount++;
            inParagraph = true;
        }
        if (trimmed.startsWith("#")) {
            headingCount++;
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches("\\d+\\.\\s+.*")) {
            listItemCount++;
        }
    }
    return new BodyStructure(paragraphCount, headingCount, listItemCount);
}

/**
 * 执行下线流程，使目标内容退出对外生效状态。
 *
 * @param draftId 草稿唯一标识
 * @param request 封装业务输入的请求对象
 * @return 方法处理后的结果对象
 */

@Override
@Transactional
public ContentDraftResponse offline(Long draftId, OfflineRequest request) {
    return offline(draftId, request, WorkflowOperatorIdentity.system());
}

/**
 * 执行下线流程，使目标内容退出对外生效状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
 *
 * @param draftId 草稿唯一标识
 * @param request 封装业务输入的请求对象
 * @param operator 当前操作人身份信息
 * @return 方法处理后的结果对象
 */

@Override
@Transactional
public ContentDraftResponse offline(Long draftId, OfflineRequest request, WorkflowOperatorIdentity operator) {
    ContentDraft draft = requireDraft(draftId);
    ensureState(draft, EnumSet.of(WorkflowStatus.PUBLISHED),
            "only PUBLISHED content can be taken offline");
    LocalDateTime now = LocalDateTime.now();
    acquireDraftOperationLock(draftId, DraftOperationType.OFFLINE, null, operator.operatorName(), now);
    try {
        draft.setStatus(WorkflowStatus.OFFLINE);
        draft.setUpdatedAt(now);
        draft = persistDraft(draft, EnumSet.of(WorkflowStatus.PUBLISHED));
        store.insertPublishLog(WorkflowAuditLogFactory.operatorAction(draftId, "OFFLINE", operator)
                .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                .targetId(draftId)
                .beforeStatus(WorkflowStatus.PUBLISHED.name())
                .afterStatus(WorkflowStatus.OFFLINE.name())
                .result(WorkflowAuditResult.SUCCESS)
                .remark(request.remark())
                .createdAt(now)
                .build());
        return toDraftResponse(draft);
    } finally {
        releaseDraftOperationLockQuietly(draftId, null);
    }
}

/**
 * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
 *
 * @param draftId 草稿唯一标识
 * @return 符合条件的结果集合
 */

@Override
@Transactional(readOnly = true)
public List<PublishTaskResponse> listPublishTasks(Long draftId) {
    requireDraft(draftId);
    return store.listPublishTasks(draftId).stream()
            .sorted(Comparator.comparing(PublishTask::getCreatedAt).reversed())
            .map(task -> new PublishTaskResponse(
                    task.getId(),
                    task.getPublishedVersion(),
                    task.getTaskType(),
                    task.getStatus(),
                    task.getRetryTimes(),
                    task.getErrorMessage(),
                    task.getCreatedAt(),
                    task.getUpdatedAt()
            ))
            .toList();
}

/**
 * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
 *
 * @param draftId 草稿唯一标识
 * @return 符合条件的结果集合
 */

@Override
@Transactional(readOnly = true)
public List<PublishCommandResponse> listPublishCommands(Long draftId) {
    requireDraft(draftId);
    return store.listPublishCommands(draftId).stream()
            .sorted(Comparator.comparing(PublishCommandEntry::getCreatedAt).reversed())
            .map(entry -> new PublishCommandResponse(
                    entry.getId(),
                    entry.getCommandType(),
                    entry.getIdempotencyKey(),
                    entry.getOperatorName(),
                    entry.getRemark(),
                    entry.getStatus(),
                    entry.getTargetPublishedVersion(),
                    entry.getSnapshotId(),
                    entry.getErrorMessage(),
                    entry.getCreatedAt(),
                    entry.getUpdatedAt()
            ))
            .toList();
}

/**
 * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
 *
 * @param draftId 草稿唯一标识
 * @return 符合条件的结果集合
 */

@Override
@Transactional(readOnly = true)
public List<PublishLogResponse> listPublishLogs(Long draftId) {
    requireDraft(draftId);
    return store.listPublishLogs(draftId).stream()
            .sorted(Comparator.comparing(PublishLogEntry::getCreatedAt).reversed())
            .map(this::toPublishLogResponse)
            .toList();
}

/**
 * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
 *
 * @param draftId 草稿唯一标识
 * @param traceId 链路追踪标识
 * @return 符合条件的结果集合
 */

@Override
@Transactional(readOnly = true)
public List<PublishLogResponse> listPublishLogTimeline(Long draftId, String traceId) {
    requireDraft(draftId);
    if (traceId == null || traceId.isBlank()) {
        throw new BusinessException("INVALID_ARGUMENT", "traceId must not be blank");
    }
    return store.listPublishLogsByTraceId(traceId.trim()).stream()
            .filter(entry -> Objects.equals(entry.getDraftId(), draftId))
            .sorted(Comparator.comparing(PublishLogEntry::getCreatedAt))
            .map(this::toPublishLogResponse)
            .toList();
}

/**
 * 根据输入条件获取对应的业务数据详情。
 *
 * @param draftId 草稿唯一标识
 * @param publishedVersion 参数 publishedVersion 对应的业务输入值
 * @return 方法处理后的结果对象
 */

@Override
@Transactional(readOnly = true)
public PublishAuditTimelineResponse getPublishAuditTimeline(Long draftId, Integer publishedVersion) {
    ContentDraft draft = requireDraft(draftId);
    int targetVersion = resolvePublishTimelineVersion(draft, publishedVersion);
    String traceId = WorkflowAuditLogFactory.publishTraceId(draftId, targetVersion);
    List<PublishLogResponse> events = listPublishLogTimeline(draftId, traceId);
    if (events.isEmpty()) {
        throw new BusinessException("PUBLISH_TIMELINE_NOT_FOUND", "publish timeline not found");
    }

    PublishLogResponse first = events.get(0);
    PublishLogResponse last = events.get(events.size() - 1);
    String requestId = events.stream()
            .map(PublishLogResponse::requestId)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    String finalStatus = last.afterStatus() != null && !last.afterStatus().isBlank()
            ? last.afterStatus()
            : draft.getStatus().name();

    return new PublishAuditTimelineResponse(
            draftId,
            targetVersion,
            traceId,
            requestId,
            first.operatorId(),
            first.operatorName(),
            draft.getStatus().name(),
            last.actionType(),
            finalStatus,
            last.result(),
            first.createdAt(),
            last.createdAt(),
            events.size(),
            events
    );
}

/**
 * 处理 to publish log response 相关逻辑，并返回对应的执行结果。
 *
 * @param entry 参数 entry 对应的业务输入值
 * @return 方法处理后的结果对象
 */

private PublishLogResponse toPublishLogResponse(PublishLogEntry entry) {
    return new PublishLogResponse(
            entry.getId(),
            entry.getTraceId(),
            entry.getRequestId(),
            entry.getActionType(),
            entry.getOperatorId(),
            entry.getOperatorName(),
            entry.getTargetType(),
            entry.getTargetId(),
            entry.getPublishedVersion(),
            entry.getTaskId(),
            entry.getOutboxEventId(),
            entry.getBeforeStatus(),
            entry.getAfterStatus(),
            entry.getResult(),
            entry.getErrorCode(),
            entry.getErrorMessage(),
            entry.getRemark(),
            entry.getCreatedAt()
    );
}

private PublishTask newPublishTask(Long draftId,
                                   Integer publishedVersion,
                                   PublishTaskType taskType,
                                   LocalDateTime now) {
    WorkflowAuditContext auditContext = WorkflowAuditContextHolder.get();
    String traceId = auditContext == null ? WorkflowLogContext.currentTraceId() : auditContext.traceId();
    String requestId = auditContext == null ? WorkflowLogContext.currentRequestId() : auditContext.requestId();
    return PublishTask.builder()
            .draftId(draftId)
            .publishedVersion(publishedVersion)
            .traceId(traceId)
            .requestId(requestId)
            .taskType(taskType)
            .status(PublishTaskStatus.PENDING)
            .retryTimes(0)
            .nextRunAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
}

/**
 * 解析输入信息并生成当前流程所需的结构化结果。
 *
 * @param draft 草稿对象
 * @param publishedVersion 参数 publishedVersion 对应的业务输入值
 * @return 统计值或数量结果
 */

private int resolvePublishTimelineVersion(ContentDraft draft, Integer publishedVersion) {
    if (publishedVersion != null) {
        if (publishedVersion <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "publishedVersion must be greater than 0");
        }
        return publishedVersion;
    }
    if (draft.getPublishedVersion() == null || draft.getPublishedVersion() <= 0) {
        throw new BusinessException("PUBLISH_TIMELINE_NOT_FOUND", "draft has no publish timeline");
    }
    return draft.getPublishedVersion();
}

/**
 * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
 */

private record PublishDiffContext(
        int basePublishedVersion,
        boolean firstPublish,
        boolean hasChanges,
        List<PublishDiffResponse.FieldDiff> fields,
        List<PublishDiffResponse.ChangeScopeSummary> scopes,
        List<PublishDiffResponse.PlannedTask> plannedTasks,
        EnumSet<PublishTaskType> plannedTaskTypes
) {
    /**
     * 处理 field changed 相关逻辑，并返回对应的执行结果。
     *
     * @param field 参数 field 对应的业务输入值
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    private boolean fieldChanged(String field) {
        return fields.stream()
                .filter(item -> Objects.equals(item.field(), field))
                .findFirst()
                .map(PublishDiffResponse.FieldDiff::changed)
                .orElse(false);
    }
}

/**
 * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
 */

private record BodyDiffAnalysis(
        boolean contentChanged,
        boolean structureChanged,
        boolean formatOnlyChanged
) {
}

/**
 * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
 */

private record BodyStructure(
        int paragraphCount,
        int headingCount,
        int listItemCount
) {
}
}

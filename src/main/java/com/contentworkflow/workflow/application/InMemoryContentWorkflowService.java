package com.contentworkflow.workflow.application;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
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
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class InMemoryContentWorkflowService implements ContentWorkflowService {

    private final WorkflowStore store;

    private static final String COMMAND_TYPE_PUBLISH = "PUBLISH";
    private static final String CMD_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String CMD_STATUS_ACCEPTED = "ACCEPTED";
    private static final String CMD_STATUS_FAILED = "FAILED";

    /**
     * Keeps existing tests (which instantiate the service directly) working.
     * Spring will prefer the {@link #InMemoryContentWorkflowService(WorkflowStore)} constructor.
     */
    public InMemoryContentWorkflowService() {
        this(new InMemoryWorkflowStore());
    }

    @Autowired
    public InMemoryContentWorkflowService(WorkflowStore store) {
        this.store = store;
    }

    @Value("${workflow.demo.seedDraft:false}")
    private boolean seedDraft;

    @PostConstruct
    public void init() {
        if (seedDraft) {
            createDraft(new CreateDraftRequest(
                    "Course Release Workflow Design",
                    "Seed draft for immediate API demo.",
                    "This seeded draft is created at startup to make the workflow easy to try."
            ), WorkflowOperatorIdentity.system());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentDraftResponse> listDrafts() {
        return store.listDrafts().stream()
                .map(this::toDraftResponse)
                .toList();
    }

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

    @Override
    @Transactional
    public ContentDraftResponse createDraft(CreateDraftRequest request) {
        // Keep legacy callers working by defaulting to the system operator.
        return createDraft(request, WorkflowOperatorIdentity.system());
    }

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

    @Override
    @Transactional
    public ContentDraftResponse updateDraft(Long draftId, UpdateDraftRequest request) {
        return updateDraft(draftId, request, WorkflowOperatorIdentity.system());
    }

    @Override
    @Transactional
    public ContentDraftResponse updateDraft(Long draftId, UpdateDraftRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        WorkflowStatus beforeStatus = draft.getStatus();
        ensureState(draft, EnumSet.of(WorkflowStatus.DRAFT, WorkflowStatus.REJECTED, WorkflowStatus.OFFLINE),
                "draft can only be edited in DRAFT, REJECTED or OFFLINE state");
        draft.setTitle(request.title());
        draft.setSummary(request.summary());
        draft.setBody(request.body());
        draft.setDraftVersion(draft.getDraftVersion() + 1);
        draft.setStatus(WorkflowStatus.DRAFT);
        draft.setLastReviewComment(null);
        draft.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(draft);
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

    @Override
    @Transactional(readOnly = true)
    public ContentDraftResponse getDraft(Long draftId) {
        return toDraftResponse(requireDraft(draftId));
    }

    @Override
    @Transactional
    public ContentDraftResponse submitReview(Long draftId, SubmitReviewRequest request) {
        return submitReview(draftId, request, WorkflowOperatorIdentity.system());
    }

    @Override
    @Transactional
    public ContentDraftResponse submitReview(Long draftId, SubmitReviewRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        WorkflowStatus beforeStatus = draft.getStatus();
        ensureState(draft, EnumSet.of(WorkflowStatus.DRAFT, WorkflowStatus.REJECTED, WorkflowStatus.OFFLINE),
                "only DRAFT, REJECTED or OFFLINE content can be submitted for review");
        draft.setStatus(WorkflowStatus.REVIEWING);
        draft.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(draft);
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

    @Override
    @Transactional
    public ContentDraftResponse review(Long draftId, ReviewDecisionRequest request) {
        return review(draftId, request, WorkflowOperatorIdentity.system());
    }

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
        store.updateDraft(draft);
        return toDraftResponse(draft);
    }

    @Override
    @Transactional
    public ContentDraftResponse publish(Long draftId, PublishRequest request) {
        return publish(draftId, request, WorkflowOperatorIdentity.system());
    }

    @Override
    @Transactional
    public ContentDraftResponse publish(Long draftId, PublishRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        WorkflowStatus beforeStatus = draft.getStatus();
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        PublishDiffContext diff = loadPublishDiff(draft, null);

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
                return toDraftResponse(draft);
            }
        }

        draft.setStatus(WorkflowStatus.PUBLISHING);
        draft.setUpdatedAt(now);
        store.updateDraft(draft);

        try {
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

            EnumSet<PublishTaskType> taskTypes = resolvePublishTaskTypes(diff);
            List<PublishTask> tasks = new ArrayList<>(taskTypes.size());
            for (PublishTaskType taskType : taskTypes) {
                tasks.add(PublishTask.builder()
                        .draftId(draftId)
                        .publishedVersion(nextPublishedVersion)
                        .taskType(taskType)
                        // Publish only persists tasks and moves the draft to PUBLISHING; workers execute asynchronously.
                        .status(PublishTaskStatus.PENDING)
                        .retryTimes(0)
                        .nextRunAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
            store.insertPublishTasks(tasks);

            draft.setPublishedVersion(nextPublishedVersion);
            draft.setCurrentSnapshotId(snapshot.getId());
            // Final completion depends on async task execution: SUCCESS => PUBLISHED, unrecoverable failure => PUBLISH_FAILED.
            draft.setUpdatedAt(now);
            store.updateDraft(draft);

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
            draft.setStatus(WorkflowStatus.PUBLISH_FAILED);
            draft.setUpdatedAt(failAt);
            store.updateDraft(draft);

            if (idempotencyKey != null) {
                store.updatePublishCommand(PublishCommandEntry.builder()
                        .draftId(draftId)
                        .commandType(COMMAND_TYPE_PUBLISH)
                        .idempotencyKey(idempotencyKey)
                        .operatorName(operator.operatorName())
                        .remark(request.remark())
                        .status(CMD_STATUS_FAILED)
                        .targetPublishedVersion(targetPublishedVersion)
                        .snapshotId(draft.getCurrentSnapshotId())
                        .errorMessage(e.getMessage())
                        .updatedAt(failAt)
                        .build());
            }
            store.insertPublishLog(WorkflowAuditLogFactory.publishAction(draftId, targetPublishedVersion, "PUBLISH_FAILED", operator)
                    .beforeStatus(WorkflowStatus.PUBLISHING.name())
                    .afterStatus(WorkflowStatus.PUBLISH_FAILED.name())
                    .result(WorkflowAuditResult.FAILED)
                    .errorCode("PUBLISH_REQUEST_FAILED")
                    .errorMessage(e.getMessage())
                    .remark(e.getMessage())
                    .createdAt(failAt)
                    .build());
            throw e;
        }
    }

    @Override
    @Transactional
    public ContentDraftResponse rollback(Long draftId, RollbackRequest request) {
        return rollback(draftId, request, WorkflowOperatorIdentity.system());
    }

    @Override
    @Transactional
    public ContentDraftResponse rollback(Long draftId, RollbackRequest request, WorkflowOperatorIdentity operator) {
        ContentDraft draft = requireDraft(draftId);
        ensureState(draft, EnumSet.of(WorkflowStatus.PUBLISHED, WorkflowStatus.OFFLINE),
                "only PUBLISHED or OFFLINE content can rollback");

        List<ContentSnapshot> snapshotList = store.listSnapshots(draftId);
        ContentSnapshot target = snapshotList.stream()
                .filter(snapshot -> snapshot.getPublishedVersion().equals(request.targetPublishedVersion()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("SNAPSHOT_NOT_FOUND", "target published version not found"));

        LocalDateTime now = LocalDateTime.now();
        draft.setStatus(WorkflowStatus.PUBLISHING);
        draft.setUpdatedAt(now);
        store.updateDraft(draft);

        int nextPublishedVersion = draft.getPublishedVersion() + 1;
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
            tasks.add(PublishTask.builder()
                    .draftId(draftId)
                    .publishedVersion(nextPublishedVersion)
                    .taskType(taskType)
                    .status(PublishTaskStatus.PENDING)
                    .retryTimes(0)
                    .nextRunAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        store.insertPublishTasks(tasks);

        draft.setTitle(target.getTitle());
        draft.setSummary(target.getSummary());
        draft.setBody(target.getBody());
        draft.setPublishedVersion(nextPublishedVersion);
        draft.setCurrentSnapshotId(rollbackSnapshot.getId());
        draft.setStatus(WorkflowStatus.PUBLISHING);
        // Rollback reuses publish task orchestration and returns to PUBLISHING until tasks complete.
        draft.setUpdatedAt(now);
        store.updateDraft(draft);

        store.insertPublishLog(WorkflowAuditLogFactory.publishAction(draftId, nextPublishedVersion, "ROLLBACK_REQUESTED", operator)
                .beforeStatus(WorkflowStatus.PUBLISHED.name())
                .afterStatus(WorkflowStatus.PUBLISHING.name())
                .result(WorkflowAuditResult.ACCEPTED)
                .remark(request.reason())
                .createdAt(now)
                .build());
        return toDraftResponse(draft);
    }

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

    private ContentDraft requireDraft(Long draftId) {
        return store.findDraftById(draftId)
                .orElseThrow(() -> new BusinessException("DRAFT_NOT_FOUND", "draft not found"));
    }

    private void ensureState(ContentDraft draft, EnumSet<WorkflowStatus> allowedStates, String message) {
        if (!allowedStates.contains(draft.getStatus())) {
            throw new BusinessException("INVALID_WORKFLOW_STATE", message + ", current state: " + draft.getStatus());
        }
    }

    private ContentDraftResponse toDraftResponse(ContentDraft draft) {
        return new ContentDraftResponse(
                draft.getId(),
                draft.getBizNo(),
                draft.getTitle(),
                draft.getSummary(),
                draft.getBody(),
                draft.getDraftVersion(),
                draft.getPublishedVersion(),
                draft.getStatus(),
                draft.getCurrentSnapshotId(),
                draft.getLastReviewComment(),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }

    private ContentDraftSummaryResponse toDraftSummaryResponse(ContentDraft draft) {
        return new ContentDraftSummaryResponse(
                draft.getId(),
                draft.getBizNo(),
                draft.getTitle(),
                draft.getSummary(),
                draft.getDraftVersion(),
                draft.getPublishedVersion(),
                draft.getStatus(),
                draft.getCurrentSnapshotId(),
                draft.getLastReviewComment(),
                draft.getCreatedAt(),
                draft.getUpdatedAt(),
                toActions(draft.getStatus())
        );
    }

    /**
     * 鍓嶇鎸夐挳寮€鍏筹細涓庢湇鍔＄鐘舵€佹満淇濇寔涓€鑷淬€?     */
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

    private DraftQueryRequest normalizeQuery(DraftQueryRequest request) {
        if (request == null) {
            return new DraftQueryRequest(null, null, false, 1, 20,
                    DraftQueryRequest.DraftSortBy.UPDATED_AT, DraftQueryRequest.SortDirection.DESC,
                    null, null, null, null).normalized();
        }
        return request.normalized();
    }

    private void validateQuery(DraftQueryRequest request) {
        // Use business errors instead of IllegalArgumentException to avoid surfacing as HTTP 500.
        if (request.createdFrom() != null && request.createdTo() != null && request.createdFrom().isAfter(request.createdTo())) {
            throw new BusinessException("INVALID_QUERY", "createdFrom must be <= createdTo");
        }
        if (request.updatedFrom() != null && request.updatedTo() != null && request.updatedFrom().isAfter(request.updatedTo())) {
            throw new BusinessException("INVALID_QUERY", "updatedFrom must be <= updatedTo");
        }
    }

    private List<ContentDraft> filterAndSortDrafts(List<ContentDraft> drafts, DraftQueryRequest request) {
        return sortDrafts(filterDrafts(drafts, request), request);
    }

    /**
     * Filtering rules without sorting so stats and paging can reuse the same predicate semantics.
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
     * Sort drafts by the requested field and direction, then append id as a stable tiebreaker.
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

private boolean containsIgnoreCase(String text, String keywordLowerCase) {
    if (text == null) {
        return false;
    }
    return text.toLowerCase().contains(keywordLowerCase);
}

private void ensurePublishHasChanges(PublishDiffContext diff) {
    if (!diff.firstPublish() && !diff.hasChanges()) {
        throw new BusinessException("NO_PUBLISH_CHANGES", "publish rejected because no content changes were detected");
    }
}

private EnumSet<PublishTaskType> resolvePublishTaskTypes(PublishDiffContext diff) {
    return diff.plannedTaskTypes();
}

/**
 * 骞傜瓑 key 澶嶇敤淇濇姢锛氬鏋?key 宸茬粡缁戝畾浜嗘煇涓揩鐓э紝鍐嶆浣跨敤鍚屼竴涓?key 鏃跺繀椤讳繚璇佽崏绋垮唴瀹逛竴鑷淬€?     *
 * <p>杩欐潯瑙勫垯涓昏鐢ㄤ簬闃叉瀹㈡埛绔澶嶇敤骞傜瓑 key锛屽鑷村彂甯冭姹傝闈欓粯蹇界暐銆?/p>
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

private int resolveDefaultBasePublishedVersion(ContentDraft draft) {
    int publishedVersion = safeInt(draft.getPublishedVersion());
    if (draft.getStatus() == WorkflowStatus.PUBLISH_FAILED && publishedVersion > 0) {
        // Retry from PUBLISH_FAILED should compare with the last stable version, not the failed target version.
        return publishedVersion - 1;
    }
    return publishedVersion;
}

private int safeInt(Integer value) {
    return value == null ? 0 : value;
}

private String safeString(String value) {
    return value == null ? "" : value;
}

private String normalizeIdempotencyKey(String key) {
    if (key == null) {
        return null;
    }
    String trimmed = key.trim();
    return trimmed.isEmpty() ? null : trimmed;
}

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

private boolean hasScope(List<PublishDiffResponse.ChangeScopeSummary> scopes, PublishChangeScope scope) {
    return scopes.stream().anyMatch(item -> item.scope() == scope && item.changed());
}

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

private String normalizeWhitespace(String text) {
    if (text == null) {
        return null;
    }
    return text.trim().replaceAll("\\s+", " ");
}

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

@Override
@Transactional
public ContentDraftResponse offline(Long draftId, OfflineRequest request) {
    return offline(draftId, request, WorkflowOperatorIdentity.system());
}

@Override
@Transactional
public ContentDraftResponse offline(Long draftId, OfflineRequest request, WorkflowOperatorIdentity operator) {
    ContentDraft draft = requireDraft(draftId);
    ensureState(draft, EnumSet.of(WorkflowStatus.PUBLISHED),
            "only PUBLISHED content can be taken offline");
    LocalDateTime now = LocalDateTime.now();
    draft.setStatus(WorkflowStatus.OFFLINE);
    draft.setUpdatedAt(now);
    store.updateDraft(draft);
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
}

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

@Override
@Transactional(readOnly = true)
public List<PublishLogResponse> listPublishLogs(Long draftId) {
    requireDraft(draftId);
    return store.listPublishLogs(draftId).stream()
            .sorted(Comparator.comparing(PublishLogEntry::getCreatedAt).reversed())
            .map(this::toPublishLogResponse)
            .toList();
}

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

private record PublishDiffContext(
        int basePublishedVersion,
        boolean firstPublish,
        boolean hasChanges,
        List<PublishDiffResponse.FieldDiff> fields,
        List<PublishDiffResponse.ChangeScopeSummary> scopes,
        List<PublishDiffResponse.PlannedTask> plannedTasks,
        EnumSet<PublishTaskType> plannedTaskTypes
) {
    private boolean fieldChanged(String field) {
        return fields.stream()
                .filter(item -> Objects.equals(item.field(), field))
                .findFirst()
                .map(PublishDiffResponse.FieldDiff::changed)
                .orElse(false);
    }
}

private record BodyDiffAnalysis(
        boolean contentChanged,
        boolean structureChanged,
        boolean formatOnlyChanged
) {
}

private record BodyStructure(
        int paragraphCount,
        int headingCount,
        int listItemCount
) {
}
}

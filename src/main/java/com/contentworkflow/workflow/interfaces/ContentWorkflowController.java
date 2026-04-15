package com.contentworkflow.workflow.interfaces;

import com.contentworkflow.common.api.ApiResponse;
import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.common.web.auth.CurrentWorkflowOperator;
import com.contentworkflow.common.web.auth.RequireWorkflowPermission;
import com.contentworkflow.common.web.auth.RequireWorkflowRole;
import com.contentworkflow.common.web.auth.WorkflowPermission;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.workflow.application.ContentWorkflowService;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
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
import com.contentworkflow.workflow.interfaces.vo.DraftWorkflowSummaryResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishCommandResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishAuditTimelineResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishDiffResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishLogResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishTaskResponse;
import com.contentworkflow.workflow.interfaces.vo.ReviewRecordResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/workflows")
public class ContentWorkflowController {

    private final ContentWorkflowService contentWorkflowService;

    public ContentWorkflowController(ContentWorkflowService contentWorkflowService) {
        this.contentWorkflowService = contentWorkflowService;
    }

    /**
     * 列出全部草稿（包含 body），仅用于调试/演示。
     *
     * <p>前端列表页请使用 {@link #pageDraftSummaries(DraftQueryRequest)}，避免拉取大字段。</p>
     */
    @GetMapping("/drafts")
    @RequireWorkflowRole({WorkflowRole.ADMIN})
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_DEBUG_READ})
    public ApiResponse<List<ContentDraftResponse>> listDrafts() {
        return ApiResponse.ok(contentWorkflowService.listDrafts());
    }

    /**
     * 草稿分页查询（列表页专用）：支持关键词、状态、时间范围、排序。
     *
     * <p>返回轻量摘要，不包含 body。</p>
     */
    @GetMapping("/drafts/page")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<PageResponse<ContentDraftSummaryResponse>> pageDraftSummaries(
            @ModelAttribute @Valid DraftQueryRequest request
    ) {
        return ApiResponse.ok(contentWorkflowService.pageDraftSummaries(request));
    }

    /**
     * 单个草稿的工作流摘要：用于详情页顶部信息区/右侧信息卡。
     */
    @GetMapping("/drafts/{draftId}/summary")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<DraftWorkflowSummaryResponse> getDraftWorkflowSummary(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.getDraftWorkflowSummary(draftId));
    }

    /**
     * 草稿统计：用于列表页的状态过滤器（Tab/Badge）。
     *
     * <p>支持与分页查询一致的过滤条件（keyword/status/time），但不包含分页参数。</p>
     */
    @GetMapping("/drafts/stats")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_STATS_READ})
    public ApiResponse<DraftStatsResponse> getDraftStats(@ModelAttribute @Valid DraftQueryRequest request) {
        return ApiResponse.ok(contentWorkflowService.getDraftStats(request));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_WRITE})
    public ApiResponse<ContentDraftResponse> createDraft(@RequestBody @Valid CreateDraftRequest request,
                                                         @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.createDraft(request, operator));
    }

    @PutMapping("/drafts/{draftId}")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_WRITE})
    public ApiResponse<ContentDraftResponse> updateDraft(@PathVariable @Min(1) Long draftId,
                                                         @RequestBody @Valid UpdateDraftRequest request,
                                                         @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.updateDraft(draftId, request, operator));
    }

    @GetMapping("/drafts/{draftId}")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<ContentDraftResponse> getDraft(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.getDraft(draftId));
    }

    @PostMapping("/drafts/{draftId}/submit-review")
    @RequireWorkflowPermission({WorkflowPermission.REVIEW_SUBMIT})
    public ApiResponse<ContentDraftResponse> submitReview(@PathVariable @Min(1) Long draftId,
                                                          @RequestBody @Valid SubmitReviewRequest request,
                                                          @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.submitReview(draftId, request, operator));
    }

    @PostMapping("/drafts/{draftId}/review")
    @RequireWorkflowPermission({WorkflowPermission.REVIEW_DECIDE})
    public ApiResponse<ContentDraftResponse> review(@PathVariable @Min(1) Long draftId,
                                                    @RequestBody @Valid ReviewDecisionRequest request,
                                                    @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.review(draftId, request, operator));
    }

    @PostMapping("/drafts/{draftId}/publish")
    @RequireWorkflowPermission({WorkflowPermission.PUBLISH_EXECUTE})
    public ApiResponse<ContentDraftResponse> publish(@PathVariable @Min(1) Long draftId,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                     @RequestBody @Valid PublishRequest request,
                                                     @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        PublishRequest normalized = request;
        if ((normalized.idempotencyKey() == null || normalized.idempotencyKey().isBlank())
                && idempotencyKey != null && !idempotencyKey.isBlank()) {
            normalized = new PublishRequest(normalized.remark(), idempotencyKey);
        }
        return ApiResponse.ok(contentWorkflowService.publish(draftId, normalized, operator));
    }

    @PostMapping("/drafts/{draftId}/rollback")
    @RequireWorkflowPermission({WorkflowPermission.ROLLBACK_EXECUTE})
    public ApiResponse<ContentDraftResponse> rollback(@PathVariable @Min(1) Long draftId,
                                                      @RequestBody @Valid RollbackRequest request,
                                                      @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.rollback(draftId, request, operator));
    }

    /**
     * 发布差异预览：对比「当前草稿」与「基线已发布版本（默认为最新已发布版本）」之间的差异。
     *
     * <p>用于前端发布前的变更确认页。</p>
     */
    @GetMapping("/drafts/{draftId}/publish-diff")
    @RequireWorkflowPermission({WorkflowPermission.PUBLISH_DIFF_READ})
    public ApiResponse<PublishDiffResponse> getPublishDiff(@PathVariable @Min(1) Long draftId,
                                                           @RequestParam(value = "basePublishedVersion", required = false) Integer basePublishedVersion) {
        return ApiResponse.ok(contentWorkflowService.getPublishDiff(draftId, basePublishedVersion));
    }

    @GetMapping("/drafts/{draftId}/reviews")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<List<ReviewRecordResponse>> listReviews(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listReviews(draftId));
    }

    @GetMapping("/drafts/{draftId}/snapshots")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<List<ContentSnapshotResponse>> listSnapshots(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listSnapshots(draftId));
    }

    @PostMapping("/drafts/{draftId}/offline")
    @RequireWorkflowPermission({WorkflowPermission.OFFLINE_EXECUTE})
    public ApiResponse<ContentDraftResponse> offline(@PathVariable @Min(1) Long draftId,
                                                     @RequestBody @Valid OfflineRequest request,
                                                     @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.offline(draftId, request, operator));
    }

    @GetMapping("/drafts/{draftId}/tasks")
    @RequireWorkflowPermission({WorkflowPermission.TASK_VIEW})
    public ApiResponse<List<PublishTaskResponse>> listPublishTasks(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listPublishTasks(draftId));
    }

    /**
     * 幂等发布命令列表：用于查看同一草稿的发布请求历史和命中情况。
     */
    @GetMapping("/drafts/{draftId}/commands")
    @RequireWorkflowPermission({WorkflowPermission.COMMAND_VIEW})
    public ApiResponse<List<PublishCommandResponse>> listPublishCommands(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listPublishCommands(draftId));
    }

    @GetMapping("/drafts/{draftId}/logs")
    @RequireWorkflowPermission({WorkflowPermission.LOG_VIEW})
    public ApiResponse<List<PublishLogResponse>> listPublishLogs(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listPublishLogs(draftId));
    }

    @GetMapping("/drafts/{draftId}/logs/timeline")
    @RequireWorkflowPermission({WorkflowPermission.LOG_VIEW})
    public ApiResponse<List<PublishLogResponse>> listPublishLogTimeline(@PathVariable @Min(1) Long draftId,
                                                                        @RequestParam("traceId") String traceId) {
        return ApiResponse.ok(contentWorkflowService.listPublishLogTimeline(draftId, traceId));
    }

    @GetMapping("/drafts/{draftId}/logs/publish-timeline")
    @RequireWorkflowPermission({WorkflowPermission.LOG_VIEW})
    public ApiResponse<PublishAuditTimelineResponse> getPublishAuditTimeline(@PathVariable @Min(1) Long draftId,
                                                                             @RequestParam(value = "publishedVersion", required = false) Integer publishedVersion) {
        return ApiResponse.ok(contentWorkflowService.getPublishAuditTimeline(draftId, publishedVersion));
    }
}

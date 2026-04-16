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

/**
 * 接口层控制器，负责接收 HTTP 请求、完成参数校验与权限约束，并将业务处理委托给应用服务。
 */

@RestController
@Validated
@RequestMapping("/api/workflows")
public class ContentWorkflowController {

    private final ContentWorkflowService contentWorkflowService;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param contentWorkflowService 参数 contentWorkflowService 对应的业务输入值
     */

    public ContentWorkflowController(ContentWorkflowService contentWorkflowService) {
        this.contentWorkflowService = contentWorkflowService;
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @return 统一封装后的接口响应结果
     */
    @GetMapping("/drafts")
    @RequireWorkflowRole({WorkflowRole.ADMIN})
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_DEBUG_READ})
    public ApiResponse<List<ContentDraftResponse>> listDrafts() {
        return ApiResponse.ok(contentWorkflowService.listDrafts());
    }

    /**
     * 按分页条件查询数据，并返回包含分页元信息的结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 统一封装后的接口响应结果
     */
    @GetMapping("/drafts/page")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<PageResponse<ContentDraftSummaryResponse>> pageDraftSummaries(
            @ModelAttribute @Valid DraftQueryRequest request
    ) {
        return ApiResponse.ok(contentWorkflowService.pageDraftSummaries(request));
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @return 统一封装后的接口响应结果
     */
    @GetMapping("/drafts/{draftId}/summary")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<DraftWorkflowSummaryResponse> getDraftWorkflowSummary(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.getDraftWorkflowSummary(draftId));
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param request 封装业务输入的请求对象
     * @return 统一封装后的接口响应结果
     */
    @GetMapping("/drafts/stats")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_STATS_READ})
    public ApiResponse<DraftStatsResponse> getDraftStats(@ModelAttribute @Valid DraftQueryRequest request) {
        return ApiResponse.ok(contentWorkflowService.getDraftStats(request));
    }

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_WRITE})
    public ApiResponse<ContentDraftResponse> createDraft(@RequestBody @Valid CreateDraftRequest request,
                                                         @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.createDraft(request, operator));
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PutMapping("/drafts/{draftId}")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_WRITE})
    public ApiResponse<ContentDraftResponse> updateDraft(@PathVariable @Min(1) Long draftId,
                                                         @RequestBody @Valid UpdateDraftRequest request,
                                                         @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.updateDraft(draftId, request, operator));
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/drafts/{draftId}")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<ContentDraftResponse> getDraft(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.getDraft(draftId));
    }

    /**
     * 提交当前业务动作，推动流程进入下一处理阶段。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/drafts/{draftId}/submit-review")
    @RequireWorkflowPermission({WorkflowPermission.REVIEW_SUBMIT})
    public ApiResponse<ContentDraftResponse> submitReview(@PathVariable @Min(1) Long draftId,
                                                          @RequestBody @Valid SubmitReviewRequest request,
                                                          @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.submitReview(draftId, request, operator));
    }

    /**
     * 执行审核动作，并根据审核结果更新流程状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/drafts/{draftId}/review")
    @RequireWorkflowPermission({WorkflowPermission.REVIEW_DECIDE})
    public ApiResponse<ContentDraftResponse> review(@PathVariable @Min(1) Long draftId,
                                                    @RequestBody @Valid ReviewDecisionRequest request,
                                                    @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.review(draftId, request, operator));
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param idempotencyKey 参数 idempotencyKey 对应的业务输入值
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

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

    /**
     * 执行回滚流程，将业务状态恢复到目标版本或阶段。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/drafts/{draftId}/rollback")
    @RequireWorkflowPermission({WorkflowPermission.ROLLBACK_EXECUTE})
    public ApiResponse<ContentDraftResponse> rollback(@PathVariable @Min(1) Long draftId,
                                                      @RequestBody @Valid RollbackRequest request,
                                                      @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.rollback(draftId, request, operator));
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @param basePublishedVersion 参数 basePublishedVersion 对应的业务输入值
     * @return 统一封装后的接口响应结果
     */
    @GetMapping("/drafts/{draftId}/publish-diff")
    @RequireWorkflowPermission({WorkflowPermission.PUBLISH_DIFF_READ})
    public ApiResponse<PublishDiffResponse> getPublishDiff(@PathVariable @Min(1) Long draftId,
                                                           @RequestParam(value = "basePublishedVersion", required = false) Integer basePublishedVersion) {
        return ApiResponse.ok(contentWorkflowService.getPublishDiff(draftId, basePublishedVersion));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/drafts/{draftId}/reviews")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<List<ReviewRecordResponse>> listReviews(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listReviews(draftId));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/drafts/{draftId}/snapshots")
    @RequireWorkflowPermission({WorkflowPermission.DRAFT_READ})
    public ApiResponse<List<ContentSnapshotResponse>> listSnapshots(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listSnapshots(draftId));
    }

    /**
     * 执行下线流程，使目标内容退出对外生效状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/drafts/{draftId}/offline")
    @RequireWorkflowPermission({WorkflowPermission.OFFLINE_EXECUTE})
    public ApiResponse<ContentDraftResponse> offline(@PathVariable @Min(1) Long draftId,
                                                     @RequestBody @Valid OfflineRequest request,
                                                     @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        return ApiResponse.ok(contentWorkflowService.offline(draftId, request, operator));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/drafts/{draftId}/tasks")
    @RequireWorkflowPermission({WorkflowPermission.TASK_VIEW})
    public ApiResponse<List<PublishTaskResponse>> listPublishTasks(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listPublishTasks(draftId));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 统一封装后的接口响应结果
     */
    @GetMapping("/drafts/{draftId}/commands")
    @RequireWorkflowPermission({WorkflowPermission.COMMAND_VIEW})
    public ApiResponse<List<PublishCommandResponse>> listPublishCommands(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listPublishCommands(draftId));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/drafts/{draftId}/logs")
    @RequireWorkflowPermission({WorkflowPermission.LOG_VIEW})
    public ApiResponse<List<PublishLogResponse>> listPublishLogs(@PathVariable @Min(1) Long draftId) {
        return ApiResponse.ok(contentWorkflowService.listPublishLogs(draftId));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @param traceId 链路追踪标识
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/drafts/{draftId}/logs/timeline")
    @RequireWorkflowPermission({WorkflowPermission.LOG_VIEW})
    public ApiResponse<List<PublishLogResponse>> listPublishLogTimeline(@PathVariable @Min(1) Long draftId,
                                                                        @RequestParam("traceId") String traceId) {
        return ApiResponse.ok(contentWorkflowService.listPublishLogTimeline(draftId, traceId));
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/drafts/{draftId}/logs/publish-timeline")
    @RequireWorkflowPermission({WorkflowPermission.LOG_VIEW})
    public ApiResponse<PublishAuditTimelineResponse> getPublishAuditTimeline(@PathVariable @Min(1) Long draftId,
                                                                             @RequestParam(value = "publishedVersion", required = false) Integer publishedVersion) {
        return ApiResponse.ok(contentWorkflowService.getPublishAuditTimeline(draftId, publishedVersion));
    }
}

package com.contentworkflow.workflow.application;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
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

import java.util.List;

/**
 * 应用服务契约，定义内容发布工作流相关的核心业务能力和对外操作边界。
 */

public interface ContentWorkflowService {

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @return 符合条件的结果集合
     */

    List<ContentDraftResponse> listDrafts();

    /**
     * 按分页条件查询数据，并返回包含分页元信息的结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 包含分页数据和分页元信息的结果对象
     */

    PageResponse<ContentDraftSummaryResponse> pageDraftSummaries(DraftQueryRequest request);

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @return 方法处理后的结果对象
     */

    DraftWorkflowSummaryResponse getDraftWorkflowSummary(Long draftId);

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    DraftStatsResponse getDraftStats(DraftQueryRequest request);

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @param basePublishedVersion 参数 basePublishedVersion 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    PublishDiffResponse getPublishDiff(Long draftId, Integer basePublishedVersion);

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    ContentDraftResponse createDraft(CreateDraftRequest request, WorkflowOperatorIdentity operator);

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    default ContentDraftResponse createDraft(CreateDraftRequest request) {
        return createDraft(request, WorkflowOperatorIdentity.system());
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    ContentDraftResponse updateDraft(Long draftId, UpdateDraftRequest request, WorkflowOperatorIdentity operator);

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    default ContentDraftResponse updateDraft(Long draftId, UpdateDraftRequest request) {
        return updateDraft(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @return 方法处理后的结果对象
     */

    ContentDraftResponse getDraft(Long draftId);

    /**
     * 提交当前业务动作，推动流程进入下一处理阶段。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    ContentDraftResponse submitReview(Long draftId, SubmitReviewRequest request, WorkflowOperatorIdentity operator);

    /**
     * 提交当前业务动作，推动流程进入下一处理阶段。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    default ContentDraftResponse submitReview(Long draftId, SubmitReviewRequest request) {
        return submitReview(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 执行审核动作，并根据审核结果更新流程状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    ContentDraftResponse review(Long draftId, ReviewDecisionRequest request, WorkflowOperatorIdentity operator);

    /**
     * 执行审核动作，并根据审核结果更新流程状态。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    default ContentDraftResponse review(Long draftId, ReviewDecisionRequest request) {
        return review(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    ContentDraftResponse publish(Long draftId, PublishRequest request, WorkflowOperatorIdentity operator);

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    default ContentDraftResponse publish(Long draftId, PublishRequest request) {
        return publish(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 执行回滚流程，将业务状态恢复到目标版本或阶段。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    ContentDraftResponse rollback(Long draftId, RollbackRequest request, WorkflowOperatorIdentity operator);

    /**
     * 执行回滚流程，将业务状态恢复到目标版本或阶段。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    default ContentDraftResponse rollback(Long draftId, RollbackRequest request) {
        return rollback(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 执行下线流程，使目标内容退出对外生效状态。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    ContentDraftResponse offline(Long draftId, OfflineRequest request, WorkflowOperatorIdentity operator);

    /**
     * 执行下线流程，使目标内容退出对外生效状态。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    default ContentDraftResponse offline(Long draftId, OfflineRequest request) {
        return offline(draftId, request, WorkflowOperatorIdentity.system());
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<ReviewRecordResponse> listReviews(Long draftId);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<ContentSnapshotResponse> listSnapshots(Long draftId);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishTaskResponse> listPublishTasks(Long draftId);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishCommandResponse> listPublishCommands(Long draftId);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishLogResponse> listPublishLogs(Long draftId);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @param traceId 链路追踪标识
     * @return 符合条件的结果集合
     */

    List<PublishLogResponse> listPublishLogTimeline(Long draftId, String traceId);

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    PublishAuditTimelineResponse getPublishAuditTimeline(Long draftId, Integer publishedVersion);
}

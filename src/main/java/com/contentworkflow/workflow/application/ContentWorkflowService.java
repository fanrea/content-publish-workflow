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

public interface ContentWorkflowService {

    List<ContentDraftResponse> listDrafts();

    PageResponse<ContentDraftSummaryResponse> pageDraftSummaries(DraftQueryRequest request);

    DraftWorkflowSummaryResponse getDraftWorkflowSummary(Long draftId);

    DraftStatsResponse getDraftStats(DraftQueryRequest request);

    PublishDiffResponse getPublishDiff(Long draftId, Integer basePublishedVersion);

    ContentDraftResponse createDraft(CreateDraftRequest request, WorkflowOperatorIdentity operator);

    default ContentDraftResponse createDraft(CreateDraftRequest request) {
        return createDraft(request, WorkflowOperatorIdentity.system());
    }

    ContentDraftResponse updateDraft(Long draftId, UpdateDraftRequest request, WorkflowOperatorIdentity operator);

    default ContentDraftResponse updateDraft(Long draftId, UpdateDraftRequest request) {
        return updateDraft(draftId, request, WorkflowOperatorIdentity.system());
    }

    ContentDraftResponse getDraft(Long draftId);

    ContentDraftResponse submitReview(Long draftId, SubmitReviewRequest request, WorkflowOperatorIdentity operator);

    default ContentDraftResponse submitReview(Long draftId, SubmitReviewRequest request) {
        return submitReview(draftId, request, WorkflowOperatorIdentity.system());
    }

    ContentDraftResponse review(Long draftId, ReviewDecisionRequest request, WorkflowOperatorIdentity operator);

    default ContentDraftResponse review(Long draftId, ReviewDecisionRequest request) {
        return review(draftId, request, WorkflowOperatorIdentity.system());
    }

    ContentDraftResponse publish(Long draftId, PublishRequest request, WorkflowOperatorIdentity operator);

    default ContentDraftResponse publish(Long draftId, PublishRequest request) {
        return publish(draftId, request, WorkflowOperatorIdentity.system());
    }

    ContentDraftResponse rollback(Long draftId, RollbackRequest request, WorkflowOperatorIdentity operator);

    default ContentDraftResponse rollback(Long draftId, RollbackRequest request) {
        return rollback(draftId, request, WorkflowOperatorIdentity.system());
    }

    ContentDraftResponse offline(Long draftId, OfflineRequest request, WorkflowOperatorIdentity operator);

    default ContentDraftResponse offline(Long draftId, OfflineRequest request) {
        return offline(draftId, request, WorkflowOperatorIdentity.system());
    }

    List<ReviewRecordResponse> listReviews(Long draftId);

    List<ContentSnapshotResponse> listSnapshots(Long draftId);

    List<PublishTaskResponse> listPublishTasks(Long draftId);

    List<PublishCommandResponse> listPublishCommands(Long draftId);

    List<PublishLogResponse> listPublishLogs(Long draftId);

    List<PublishLogResponse> listPublishLogTimeline(Long draftId, String traceId);

    PublishAuditTimelineResponse getPublishAuditTimeline(Long draftId, Integer publishedVersion);
}

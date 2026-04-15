package com.contentworkflow.workflow.application.store;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.entity.ReviewRecord;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence abstraction for the workflow engine.
 *
 * <p>Business rules should stay in the application layer. The store only handles data access and
 * concurrency semantics.</p>
 */
public interface WorkflowStore {

    List<ContentDraft> listDrafts();

    PageResponse<ContentDraft> pageDrafts(DraftQueryRequest request);

    Map<WorkflowStatus, Long> countDraftsByStatus(DraftQueryRequest request);

    ContentDraft insertDraft(ContentDraft draft);

    Optional<ContentDraft> findDraftById(Long draftId);

    ContentDraft updateDraft(ContentDraft draft);

    ReviewRecord insertReviewRecord(ReviewRecord record);

    List<ReviewRecord> listReviewRecords(Long draftId);

    ContentSnapshot insertSnapshot(ContentSnapshot snapshot);

    List<ContentSnapshot> listSnapshots(Long draftId);

    List<PublishTask> insertPublishTasks(List<PublishTask> tasks);

    List<PublishTask> listPublishTasks(Long draftId);

    PublishTask updatePublishTask(PublishTask task);

    List<PublishTask> listPublishTasksByStatus(PublishTaskStatus status);

    List<PublishTask> claimRunnablePublishTasks(int limit, String workerId, LocalDateTime now, int lockSeconds);

    Optional<PublishCommandEntry> findPublishCommand(Long draftId, String commandType, String idempotencyKey);

    boolean tryCreatePublishCommand(PublishCommandEntry entry);

    PublishCommandEntry updatePublishCommand(PublishCommandEntry entry);

    List<PublishCommandEntry> listPublishCommands(Long draftId);

    PublishLogEntry insertPublishLog(PublishLogEntry entry);

    List<PublishLogEntry> listPublishLogs(Long draftId);

    List<PublishLogEntry> listPublishLogsByTraceId(String traceId);
}

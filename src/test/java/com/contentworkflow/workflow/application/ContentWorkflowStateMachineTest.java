package com.contentworkflow.workflow.application;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.PublishRequest;
import com.contentworkflow.workflow.interfaces.dto.ReviewDecisionRequest;
import com.contentworkflow.workflow.interfaces.dto.RollbackRequest;
import com.contentworkflow.workflow.interfaces.dto.SubmitReviewRequest;
import com.contentworkflow.workflow.interfaces.dto.UpdateDraftRequest;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftResponse;
import com.contentworkflow.workflow.interfaces.vo.ContentSnapshotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.contentworkflow.testing.BusinessExceptionAssertions.assertCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentWorkflowStateMachineTest {

    private ContentWorkflowService service;
    private WorkflowStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        service = new InMemoryContentWorkflowService(store);
    }

    @Test
    void submit_review_can_only_happen_once_before_decision() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "b"));
        ContentDraftResponse reviewing = service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        assertEquals(WorkflowStatus.REVIEWING, reviewing.status());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitReview(draft.id(), new SubmitReviewRequest("editor", "again")));
        assertCode(ex, "INVALID_WORKFLOW_STATE");

        BusinessException editEx = assertThrows(BusinessException.class,
                () -> service.updateDraft(draft.id(), new UpdateDraftRequest("t2", "s2", "b2")));
        assertCode(editEx, "INVALID_WORKFLOW_STATE");
    }

    @Test
    void publish_requires_approved_or_publish_failed() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "b"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publish(draft.id(), new PublishRequest("op", "try publish", null)));
        assertCode(ex, "INVALID_WORKFLOW_STATE");

        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        ContentDraftResponse published = service.publish(draft.id(), new PublishRequest("op", "publish v1", null));
        // 异步发布：接口返回时处于 PUBLISHING，最终由 worker 推进到 PUBLISHED。
        assertEquals(WorkflowStatus.PUBLISHING, published.status());

        BusinessException republishEx = assertThrows(BusinessException.class,
                () -> service.publish(draft.id(), new PublishRequest("op", "publish v2", null)));
        assertCode(republishEx, "INVALID_WORKFLOW_STATE");
    }

    @Test
    void review_requires_reviewing_state_and_reject_sets_last_review_comment() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "b"));

        BusinessException invalidReview = assertThrows(BusinessException.class,
                () -> service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.REJECT, "no")));
        assertCode(invalidReview, "INVALID_WORKFLOW_STATE");

        ContentDraftResponse reviewing = service.submitReview(draft.id(), new SubmitReviewRequest("editor", "please review"));
        assertEquals(WorkflowStatus.REVIEWING, reviewing.status());

        ContentDraftResponse rejected = service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.REJECT, "bad title"));
        assertEquals(WorkflowStatus.REJECTED, rejected.status());
        assertEquals("bad title", rejected.lastReviewComment());

        ContentDraftResponse edited = service.updateDraft(draft.id(), new UpdateDraftRequest("t2", "s2", "b2"));
        assertEquals(WorkflowStatus.DRAFT, edited.status());
        assertNull(edited.lastReviewComment());
        assertEquals(rejected.draftVersion() + 1, edited.draftVersion());
    }

    @Test
    void rollback_requires_published_and_target_snapshot_must_exist() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "b"));

        BusinessException notPublished = assertThrows(BusinessException.class,
                () -> service.rollback(draft.id(), new RollbackRequest("op", 1, "rollback")));
        assertCode(notPublished, "INVALID_WORKFLOW_STATE");

        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));
        ContentDraftResponse published = service.publish(draft.id(), new PublishRequest("op", "publish v1", null));
        assertEquals(1, published.publishedVersion());

        // publish 接口返回时是 PUBLISHING；这里模拟 worker 完成任务后推进到 PUBLISHED，再走 rollback。
        ContentDraft persisted = store.findDraftById(draft.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);

        BusinessException noSnapshot = assertThrows(BusinessException.class,
                () -> service.rollback(draft.id(), new RollbackRequest("op", 999, "rollback")));
        assertCode(noSnapshot, "SNAPSHOT_NOT_FOUND");

        ContentDraftResponse rolledBack = service.rollback(draft.id(), new RollbackRequest("op", 1, "rollback to v1"));
        assertEquals(WorkflowStatus.PUBLISHING, rolledBack.status());
        assertEquals(2, rolledBack.publishedVersion());

        List<ContentSnapshotResponse> snapshots = service.listSnapshots(draft.id());
        assertTrue(snapshots.size() >= 2, "should contain original publish and rollback snapshot");
        assertEquals(2, snapshots.get(0).publishedVersion(), "snapshots should be sorted desc by version");
        assertEquals(1, snapshots.get(1).publishedVersion());
        assertTrue(snapshots.get(0).rollback(), "latest snapshot should be a rollback snapshot");
    }
}

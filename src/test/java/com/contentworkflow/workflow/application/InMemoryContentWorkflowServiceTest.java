package com.contentworkflow.workflow.application;

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
import com.contentworkflow.workflow.interfaces.vo.ContentDraftResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryContentWorkflowServiceTest {

    private ContentWorkflowService service;
    private WorkflowStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        service = new InMemoryContentWorkflowService(store);
    }

    @Test
    void should_complete_publish_and_rollback_flow() {
        ContentDraftResponse created = service.createDraft(new CreateDraftRequest(
                "Release workflow",
                "summary",
                "body"
        ));
        assertEquals(WorkflowStatus.DRAFT, created.status());

        ContentDraftResponse submitted = service.submitReview(created.id(), new SubmitReviewRequest("editor-a", "ready"));
        assertEquals(WorkflowStatus.REVIEWING, submitted.status());

        ContentDraftResponse approved = service.review(created.id(), new ReviewDecisionRequest("reviewer-a", ReviewDecision.APPROVE, "ok"));
        assertEquals(WorkflowStatus.APPROVED, approved.status());

        ContentDraftResponse published = service.publish(created.id(), new PublishRequest("operator-a", "release v1", null));
        // publish 是异步语义：先进入 PUBLISHING，后续由 worker 完成任务并推进到 PUBLISHED。
        assertEquals(WorkflowStatus.PUBLISHING, published.status());
        assertEquals(1, published.publishedVersion());

        // 测试里手动把草稿推进到 PUBLISHED，模拟发布任务全部完成。
        ContentDraft persisted = store.findDraftById(created.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);

        ContentDraftResponse rolledBack = service.rollback(created.id(), new RollbackRequest("operator-a", 1, "rollback check"));
        assertEquals(WorkflowStatus.PUBLISHING, rolledBack.status());
        assertEquals(2, rolledBack.publishedVersion());
    }
}

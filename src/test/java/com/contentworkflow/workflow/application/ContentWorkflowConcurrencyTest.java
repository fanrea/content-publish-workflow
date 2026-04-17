package com.contentworkflow.workflow.application;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.application.store.DraftOperationLockEntry;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.PublishRequest;
import com.contentworkflow.workflow.interfaces.dto.ReviewDecisionRequest;
import com.contentworkflow.workflow.interfaces.dto.SubmitReviewRequest;
import com.contentworkflow.workflow.interfaces.dto.UpdateDraftRequest;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.contentworkflow.testing.BusinessExceptionAssertions.assertCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */
class ContentWorkflowConcurrencyTest {

    private ContentWorkflowService service;
    private InMemoryWorkflowStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        service = new InMemoryContentWorkflowService(store);
    }

    @Test
    void updateDraft_shouldRejectStaleExpectedVersion() {
        ContentDraftResponse created = service.createDraft(new CreateDraftRequest("t1", "s1", "b1"));

        ContentDraftResponse firstUpdate = service.updateDraft(
                created.id(),
                new UpdateDraftRequest(created.version(), "t2", "s2", "b2")
        );
        assertEquals(created.version() + 1, firstUpdate.version());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateDraft(
                created.id(),
                new UpdateDraftRequest(created.version(), "t3", "s3", "b3")
        ));
        assertCode(ex, "CONCURRENT_MODIFICATION");
    }

    @Test
    void updateDraft_shouldRequireExpectedVersion() {
        ContentDraftResponse created = service.createDraft(new CreateDraftRequest("t1", "s1", "b1"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateDraft(
                created.id(),
                new UpdateDraftRequest("t2", "s2", "b2")
        ));

        assertCode(ex, "VERSION_REQUIRED");
    }

    @Test
    void publish_shouldBeBlockedWhenDraftOperationLockIsHeldAcrossPublishFailure() {
        ContentDraftResponse draft = createApprovedDraft();
        service.publish(draft.id(), new PublishRequest("publish v1", "idempotency-1"));

        var persisted = store.findDraftById(draft.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISH_FAILED);
        persisted.setUpdatedAt(LocalDateTime.now());
        persisted = store.updateDraft(persisted, java.util.EnumSet.of(WorkflowStatus.PUBLISHING));
        persisted.setBody("b2");
        persisted.setDraftVersion(persisted.getDraftVersion() + 1);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted, java.util.EnumSet.of(WorkflowStatus.PUBLISH_FAILED));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.publish(draft.id(), new PublishRequest("publish retry", "idempotency-2"))
        );

        assertCode(ex, "DRAFT_OPERATION_IN_PROGRESS");
    }

    private ContentDraftResponse createApprovedDraft() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t1", "s1", "b1"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));
        return draft;
    }
}

package com.contentworkflow.workflow.application;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.OfflineRequest;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.PublishRequest;
import com.contentworkflow.workflow.interfaces.dto.ReviewDecisionRequest;
import com.contentworkflow.workflow.interfaces.dto.SubmitReviewRequest;
import com.contentworkflow.workflow.interfaces.dto.UpdateDraftRequest;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishCommandResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishDiffResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static com.contentworkflow.testing.BusinessExceptionAssertions.assertCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class PublishIdempotencyAndDiffTest {

    private ContentWorkflowService service;
    private WorkflowStore store;

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        service = new InMemoryContentWorkflowService(store);
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     */

    @Test
    void publish_should_be_idempotent_by_key_and_publish_diff_should_reflect_changes() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "b"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        ContentDraftResponse first = service.publish(draft.id(), new PublishRequest("op", "publish v1", "k1"));
        assertEquals(WorkflowStatus.PUBLISHING, first.status());
        assertEquals(1, first.publishedVersion());

        int snapshotCountAfterFirst = store.listSnapshots(draft.id()).size();
        int taskCountAfterFirst = store.listPublishTasks(draft.id()).size();

        // Same idempotency key: should not create a new snapshot/tasks, and must not fail even if state already PUBLISHING.
        ContentDraftResponse second = service.publish(draft.id(), new PublishRequest("op", "publish v1", "k1"));
        assertEquals(1, second.publishedVersion());
        assertEquals(snapshotCountAfterFirst, store.listSnapshots(draft.id()).size());
        assertEquals(taskCountAfterFirst, store.listPublishTasks(draft.id()).size());
        assertEquals(PublishTaskType.values().length, taskCountAfterFirst);

        List<PublishCommandResponse> commands = service.listPublishCommands(draft.id());
        assertEquals(1, commands.size());
        assertEquals("PUBLISH", commands.get(0).commandType());
        assertEquals("k1", commands.get(0).idempotencyKey());
        assertFalse(commands.get(0).status().isBlank());

        // Simulate worker completion to allow offline + edit.
        ContentDraft persisted = store.findDraftById(draft.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);
        store.releaseDraftOperationLock(draft.id(), 1);

        service.offline(draft.id(), new OfflineRequest("op", "offline for next edit"));
        service.updateDraft(draft.id(), new UpdateDraftRequest(service.getDraft(draft.id()).version(), "t2", "s", "b"));

        PublishDiffResponse diff = service.getPublishDiff(draft.id(), null);
        assertNotNull(diff);
        assertTrue(diff.hasChanges());
        PublishDiffResponse.FieldDiff titleDiff = diff.fields().stream()
                .filter(f -> "title".equals(f.field()))
                .findFirst()
                .orElseThrow();
        assertTrue(titleDiff.changed());
    }

    /**
     * 处理 second_publish_should_only_create_tasks_required_by_diff 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void second_publish_should_only_create_tasks_required_by_diff() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "body-v1"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        ContentDraftResponse firstPublish = service.publish(draft.id(), new PublishRequest("op", "publish v1", null));
        assertEquals(1, firstPublish.publishedVersion());

        ContentDraft persisted = store.findDraftById(draft.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);
        store.releaseDraftOperationLock(draft.id(), 1);

        service.offline(draft.id(), new OfflineRequest("op", "edit next version"));
        service.updateDraft(draft.id(), new UpdateDraftRequest(service.getDraft(draft.id()).version(), "t", "s", "body-v2"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready again"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        ContentDraftResponse secondPublish = service.publish(draft.id(), new PublishRequest("op", "publish v2", null));
        assertEquals(WorkflowStatus.PUBLISHING, secondPublish.status());
        assertEquals(2, secondPublish.publishedVersion());

        EnumSet<PublishTaskType> taskTypesForV2 = store.listPublishTasks(draft.id()).stream()
                .filter(task -> task.getPublishedVersion() == 2)
                .map(task -> task.getTaskType())
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(PublishTaskType.class)));

        assertEquals(
                EnumSet.of(PublishTaskType.REFRESH_SEARCH_INDEX, PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL),
                taskTypesForV2
        );
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     */

    @Test
    void publish_should_reject_when_no_diff_exists_after_first_publish() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "body-v1"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        service.publish(draft.id(), new PublishRequest("op", "publish v1", null));

        ContentDraft persisted = store.findDraftById(draft.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);
        store.releaseDraftOperationLock(draft.id(), 1);

        service.offline(draft.id(), new OfflineRequest("op", "edit next version"));
        service.updateDraft(draft.id(), new UpdateDraftRequest(service.getDraft(draft.id()).version(), "t", "s", "body-v1"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready again"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publish(draft.id(), new PublishRequest("op", "publish same content", null)));
        assertCode(ex, "NO_PUBLISH_CHANGES");
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     */

    @Test
    void publish_should_reject_reusing_idempotency_key_for_different_payload() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "b"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        service.publish(draft.id(), new PublishRequest("op", "publish v1", "k-reuse"));

        ContentDraft persisted = store.findDraftById(draft.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);
        store.releaseDraftOperationLock(draft.id(), 1);

        service.offline(draft.id(), new OfflineRequest("op", "edit next version"));
        service.updateDraft(draft.id(), new UpdateDraftRequest(service.getDraft(draft.id()).version(), "t2", "s", "b"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready again"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publish(draft.id(), new PublishRequest("op", "publish v2 but reuse key", "k-reuse")));
        assertCode(ex, "IDEMPOTENCY_KEY_REUSED");
    }

    /**
     * 处理 format_only_change_should_only_create_read_model_sync_task 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void format_only_change_should_only_create_read_model_sync_task() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "body-v1"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        service.publish(draft.id(), new PublishRequest("op", "publish v1", null));

        ContentDraft persisted = store.findDraftById(draft.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);
        store.releaseDraftOperationLock(draft.id(), 1);

        service.offline(draft.id(), new OfflineRequest("op", "format tweak"));
        // Only whitespace changes compared to v1.
        service.updateDraft(draft.id(), new UpdateDraftRequest(service.getDraft(draft.id()).version(), "t", "s", "body-v1  "));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready again"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        ContentDraftResponse secondPublish = service.publish(draft.id(), new PublishRequest("op", "publish v2 format only", null));
        assertEquals(2, secondPublish.publishedVersion());

        EnumSet<PublishTaskType> taskTypesForV2 = store.listPublishTasks(draft.id()).stream()
                .filter(task -> task.getPublishedVersion() == 2)
                .map(task -> task.getTaskType())
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(PublishTaskType.class)));

        assertEquals(EnumSet.of(PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL), taskTypesForV2);
    }

    /**
     * 处理 metadata_only_change_should_create_notification_and_index_refresh_tasks 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void metadata_only_change_should_create_notification_and_index_refresh_tasks() {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest("t", "s", "body-v1"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        service.publish(draft.id(), new PublishRequest("op", "publish v1", null));

        ContentDraft persisted = store.findDraftById(draft.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);
        store.releaseDraftOperationLock(draft.id(), 1);

        service.offline(draft.id(), new OfflineRequest("op", "edit title"));
        service.updateDraft(draft.id(), new UpdateDraftRequest(service.getDraft(draft.id()).version(), "t-new", "s", "body-v1"));
        service.submitReview(draft.id(), new SubmitReviewRequest("editor", "ready again"));
        service.review(draft.id(), new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        ContentDraftResponse secondPublish = service.publish(draft.id(), new PublishRequest("op", "publish v2 title changed", null));
        assertEquals(2, secondPublish.publishedVersion());

        EnumSet<PublishTaskType> taskTypesForV2 = store.listPublishTasks(draft.id()).stream()
                .filter(task -> task.getPublishedVersion() == 2)
                .map(task -> task.getTaskType())
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(PublishTaskType.class)));

        assertEquals(
                EnumSet.of(PublishTaskType.REFRESH_SEARCH_INDEX, PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL, PublishTaskType.SEND_PUBLISH_NOTIFICATION),
                taskTypesForV2
        );
    }
}

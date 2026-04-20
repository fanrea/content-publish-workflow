package com.contentworkflow.workflow.application;

import com.contentworkflow.common.web.auth.WorkflowAuditContext;
import com.contentworkflow.common.web.auth.WorkflowAuditContextHolder;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.PublishTask;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class ContentWorkflowApplicationServiceTest {

    private ContentWorkflowService service;
    private WorkflowStore store;

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        service = new ContentWorkflowApplicationService(store);
    }

    /**
     * 执行针对当前业务场景的断言验证，确认系统行为符合预期。
     */

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
        store.releaseDraftOperationLock(created.id(), 1);

        ContentDraftResponse rolledBack = service.rollback(created.id(), new RollbackRequest("operator-a", 1, "rollback check"));
        assertEquals(WorkflowStatus.PUBLISHING, rolledBack.status());
        assertEquals(2, rolledBack.publishedVersion());
    }

    @Test
    void should_persist_publish_task_trace_context_from_business_context() {
        ContentDraftResponse created = service.createDraft(new CreateDraftRequest(
                "Trace workflow",
                "summary",
                "body"
        ));
        service.submitReview(created.id(), new SubmitReviewRequest("editor-a", "ready"));
        service.review(created.id(), new ReviewDecisionRequest("reviewer-a", ReviewDecision.APPROVE, "ok"));

        WorkflowAuditContextHolder.set(new WorkflowAuditContext("trace-publish-001", "request-publish-001"));
        try {
            service.publish(created.id(), new PublishRequest("operator-a", "release v1", null));
        } finally {
            WorkflowAuditContextHolder.clear();
        }

        List<PublishTask> tasks = store.listPublishTasks(created.id());
        assertEquals(3, tasks.size());
        tasks.forEach(task -> {
            assertEquals("trace-publish-001", task.getTraceId());
            assertEquals("request-publish-001", task.getRequestId());
        });

        ContentDraft persisted = store.findDraftById(created.id()).orElseThrow();
        persisted.setStatus(WorkflowStatus.PUBLISHED);
        persisted.setUpdatedAt(LocalDateTime.now());
        store.updateDraft(persisted);
        store.releaseDraftOperationLock(created.id(), 1);

        WorkflowAuditContextHolder.set(new WorkflowAuditContext("trace-rollback-001", "request-rollback-001"));
        try {
            service.rollback(created.id(), new RollbackRequest("operator-a", 1, "rollback check"));
        } finally {
            WorkflowAuditContextHolder.clear();
        }

        List<PublishTask> rollbackTasks = store.listPublishTasks(created.id()).stream()
                .filter(task -> task.getPublishedVersion() == 2)
                .toList();
        assertEquals(3, rollbackTasks.size());
        rollbackTasks.forEach(task -> {
            assertEquals("trace-rollback-001", task.getTraceId());
            assertEquals("request-rollback-001", task.getRequestId());
        });
    }
}

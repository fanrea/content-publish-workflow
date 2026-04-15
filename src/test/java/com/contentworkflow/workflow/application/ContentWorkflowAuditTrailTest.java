package com.contentworkflow.workflow.application;

import com.contentworkflow.common.web.auth.WorkflowAuditContext;
import com.contentworkflow.common.web.auth.WorkflowAuditContextHolder;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.application.store.WorkflowAuditLogFactory;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.PublishRequest;
import com.contentworkflow.workflow.interfaces.dto.ReviewDecisionRequest;
import com.contentworkflow.workflow.interfaces.dto.SubmitReviewRequest;
import com.contentworkflow.workflow.interfaces.vo.PublishAuditTimelineResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishLogResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentWorkflowAuditTrailTest {

    private InMemoryWorkflowStore store;
    private ContentWorkflowService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        service = new InMemoryContentWorkflowService(store);
    }

    @AfterEach
    void tearDown() {
        WorkflowAuditContextHolder.clear();
    }

    @Test
    void publish_logs_should_expose_structured_audit_fields_and_trace_timeline() {
        WorkflowOperatorIdentity editor = new WorkflowOperatorIdentity("u-editor", "editor", WorkflowRole.EDITOR);
        WorkflowOperatorIdentity reviewer = new WorkflowOperatorIdentity("u-reviewer", "reviewer", WorkflowRole.REVIEWER);
        WorkflowOperatorIdentity operator = new WorkflowOperatorIdentity("u-operator", "operator", WorkflowRole.OPERATOR);

        Long draftId;
        try {
            WorkflowAuditContextHolder.set(new WorkflowAuditContext("gateway-trace-1", "req-1001"));
            draftId = service.createDraft(new CreateDraftRequest("title", "summary", "body"), editor).id();
            service.submitReview(draftId, new SubmitReviewRequest("editor", "submit"), editor);
            service.review(draftId, new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"), reviewer);
            service.publish(draftId, new PublishRequest("ship it", "idem-1"), operator);
        } finally {
            WorkflowAuditContextHolder.clear();
        }

        String publishTraceId = WorkflowAuditLogFactory.publishTraceId(draftId, 1);
        store.insertPublishLog(WorkflowAuditLogFactory.publishSystemAction(
                        draftId,
                        1,
                        "PUBLISH_COMPLETED",
                        "worker-1",
                        "worker-1"
                )
                .beforeStatus("PUBLISHING")
                .afterStatus("PUBLISHED")
                .result(WorkflowAuditResult.SUCCESS)
                .remark("version=1")
                .createdAt(LocalDateTime.now().plusSeconds(1))
                .build());

        List<PublishLogResponse> logs = service.listPublishLogs(draftId);
        PublishLogResponse publishRequested = logs.stream()
                .filter(item -> "PUBLISH_REQUESTED".equals(item.actionType()))
                .findFirst()
                .orElseThrow();

        assertEquals(publishTraceId, publishRequested.traceId());
        assertEquals("req-1001", publishRequested.requestId());
        assertEquals("u-operator", publishRequested.operatorId());
        assertEquals("operator", publishRequested.operatorName());
        assertEquals("APPROVED", publishRequested.beforeStatus());
        assertEquals("PUBLISHING", publishRequested.afterStatus());
        assertEquals(WorkflowAuditResult.ACCEPTED, publishRequested.result());
        assertEquals(1, publishRequested.publishedVersion());
        assertTrue(publishRequested.remark().contains("tasks="));

        List<PublishLogResponse> timeline = service.listPublishLogTimeline(draftId, publishTraceId);
        assertEquals(2, timeline.size());
        assertEquals(List.of("PUBLISH_REQUESTED", "PUBLISH_COMPLETED"),
                timeline.stream().map(PublishLogResponse::actionType).toList());
        assertTrue(timeline.stream().allMatch(item -> publishTraceId.equals(item.traceId())));
        assertFalse(timeline.stream().anyMatch(item -> item.createdAt() == null));

        PublishAuditTimelineResponse publishTimeline = service.getPublishAuditTimeline(draftId, 1);
        assertEquals(draftId, publishTimeline.draftId());
        assertEquals(1, publishTimeline.publishedVersion());
        assertEquals(publishTraceId, publishTimeline.traceId());
        assertEquals("req-1001", publishTimeline.requestId());
        assertEquals("u-operator", publishTimeline.initiatorId());
        assertEquals("operator", publishTimeline.initiatorName());
        assertEquals("PUBLISHED", publishTimeline.finalStatus());
        assertEquals("PUBLISH_COMPLETED", publishTimeline.finalActionType());
        assertEquals(WorkflowAuditResult.SUCCESS, publishTimeline.finalResult());
        assertEquals(2, publishTimeline.totalEvents());
        assertEquals(List.of("PUBLISH_REQUESTED", "PUBLISH_COMPLETED"),
                publishTimeline.events().stream().map(PublishLogResponse::actionType).toList());
    }
}

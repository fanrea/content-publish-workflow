package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.testing.WorkflowMdcTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class WorkflowXxlJobHandlersTracePropagationTest {

    @AfterEach
    void tearDown() {
        WorkflowLogContext.clear();
    }

    @Test
    void workflowPublishTaskPollJob_shouldExposeRestoredMdcInsideWorkerAndRestoreAfterwards() throws Exception {
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenRequestId = new AtomicReference<>();

        PublishTaskWorker publishTaskWorker = mock(PublishTaskWorker.class);
        doAnswer(invocation -> {
            seenTraceId.set(WorkflowLogContext.currentTraceId());
            seenRequestId.set(WorkflowLogContext.currentRequestId());
            return null;
        }).when(publishTaskWorker).pollOnce();

        WorkflowXxlJobHandlers handlers = new WorkflowXxlJobHandlers(
                publishTaskWorker,
                mock(OutboxRelayWorker.class),
                mock(WorkflowReconciliationService.class)
        );

        WorkflowMdcTestSupport.withLoggingContext("trace-xxl-1001", "req-xxl-1001",
                () -> handlers.workflowPublishTaskPollJob("batch=10"));

        assertEquals("trace-xxl-1001", seenTraceId.get());
        assertEquals("req-xxl-1001", seenRequestId.get());
        assertNull(WorkflowLogContext.currentTraceId());
        assertNull(WorkflowLogContext.currentRequestId());
    }

    @Test
    void workflowOutboxRelayJob_shouldExposeRestoredMdcInsideRelayWorkerAndRestoreAfterwards() throws Exception {
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenRequestId = new AtomicReference<>();

        OutboxRelayWorker outboxRelayWorker = mock(OutboxRelayWorker.class);
        doAnswer(invocation -> {
            seenTraceId.set(WorkflowLogContext.currentTraceId());
            seenRequestId.set(WorkflowLogContext.currentRequestId());
            return null;
        }).when(outboxRelayWorker).pollOnce();

        WorkflowXxlJobHandlers handlers = new WorkflowXxlJobHandlers(
                mock(PublishTaskWorker.class),
                outboxRelayWorker,
                mock(WorkflowReconciliationService.class)
        );

        WorkflowMdcTestSupport.withLoggingContext("trace-xxl-2002", "req-xxl-2002",
                () -> handlers.workflowOutboxRelayJob("batch=10"));

        assertEquals("trace-xxl-2002", seenTraceId.get());
        assertEquals("req-xxl-2002", seenRequestId.get());
        assertNull(WorkflowLogContext.currentTraceId());
        assertNull(WorkflowLogContext.currentRequestId());
    }
}

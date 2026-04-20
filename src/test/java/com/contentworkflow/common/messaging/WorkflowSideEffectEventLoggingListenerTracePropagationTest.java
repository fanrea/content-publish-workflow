package com.contentworkflow.common.messaging;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.testing.WorkflowMdcTestSupport;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;
import com.contentworkflow.workflow.application.task.PublishTaskProgressService;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkflowSideEffectEventLoggingListenerTracePropagationTest {

    @AfterEach
    void tearDown() {
        WorkflowLogContext.clear();
    }

    @Test
    void listenerFlow_shouldSeeRestoredTraceFromMessageHeadersAndRestoreAfterwards() throws Exception {
        InMemoryWorkflowStore store = new InMemoryWorkflowStore();
        Long taskId = seedAwaitingConfirmationTask(store);

        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenRequestId = new AtomicReference<>();

        WorkflowSideEffectConsumerService consumerService = new WorkflowSideEffectConsumerService(
                payload -> {
                },
                payload -> {
                    seenTraceId.set(WorkflowLogContext.currentTraceId());
                    seenRequestId.set(WorkflowLogContext.currentRequestId());
                },
                payload -> {
                },
                new PublishTaskProgressService(store, event -> {
                })
        );

        WorkflowSideEffectEventLoggingListener listener = new WorkflowSideEffectEventLoggingListener(
                new ObjectMapper().findAndRegisterModules(),
                new WorkflowMessageDeduplicationGuard(new ConcurrentMapCacheManager("cpw:mq:consumed:message")),
                consumerService
        );

        PublishTaskEventFactory.ReadModelSyncRequestedPayload payload =
                new PublishTaskEventFactory.ReadModelSyncRequestedPayload(
                        taskId,
                        1L,
                        "CPW-1",
                        21L,
                        2,
                        3,
                        "title",
                        "summary",
                        "body",
                        false,
                        "operator-a",
                        LocalDateTime.now()
                );

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(WorkflowLogContext.TRACE_ID_HEADER, "trace-mq-1001");
        headers.put(WorkflowLogContext.REQUEST_ID_HEADER, "req-mq-1001");

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(payload);
        WorkflowMdcTestSupport.withLoggingContextFromHeaders(headers, () ->
                listener.onReadModelSyncRequested(json, "msg-1", "DOWNSTREAM_READ_MODEL_SYNC_REQUESTED", headers));

        assertEquals("trace-mq-1001", seenTraceId.get());
        assertEquals("req-mq-1001", seenRequestId.get());
        assertNull(WorkflowLogContext.currentTraceId());
        assertNull(WorkflowLogContext.currentRequestId());
    }

    private Long seedAwaitingConfirmationTask(InMemoryWorkflowStore store) {
        LocalDateTime now = LocalDateTime.now();
        ContentDraft draft = store.insertDraft(ContentDraft.builder()
                .bizNo("CPW-1")
                .title("title")
                .summary("summary")
                .body("body")
                .draftVersion(3)
                .publishedVersion(2)
                .status(WorkflowStatus.PUBLISHING)
                .createdAt(now)
                .updatedAt(now)
                .build());

        PublishTask task = store.insertPublishTasks(List.of(PublishTask.builder()
                .draftId(draft.getId())
                .publishedVersion(2)
                .taskType(PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL)
                .status(PublishTaskStatus.AWAITING_CONFIRMATION)
                .retryTimes(0)
                .createdAt(now)
                .updatedAt(now)
                .build())).get(0);
        return task.getId();
    }
}

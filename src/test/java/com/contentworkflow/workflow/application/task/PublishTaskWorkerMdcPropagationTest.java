package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.testing.WorkflowMdcTestSupport;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublishTaskWorkerMdcPropagationTest {

    @AfterEach
    void tearDown() {
        WorkflowLogContext.clear();
    }

    @Test
    void pollOnce_shouldExposeRestoredMdcInsideTaskHandlerAndRestoreAfterwards() throws Exception {
        InMemoryWorkflowStore store = new InMemoryWorkflowStore();
        Long draftId = seedRunnableTask(store);

        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenRequestId = new AtomicReference<>();

        PublishTaskHandler handler = new PublishTaskHandler() {
            @Override
            public PublishTaskType taskType() {
                return PublishTaskType.REFRESH_SEARCH_INDEX;
            }

            @Override
            public void execute(PublishTaskContext ctx) {
                seenTraceId.set(WorkflowLogContext.currentTraceId());
                seenRequestId.set(WorkflowLogContext.currentRequestId());
            }
        };

        PublishTaskWorker worker = new PublishTaskWorker(
                store,
                List.of(handler),
                event -> {
                },
                new PublishTaskProgressService(store, event -> {
                })
        );

        ReflectionTestUtils.setField(worker, "enabled", true);
        ReflectionTestUtils.setField(worker, "batchSize", 10);
        ReflectionTestUtils.setField(worker, "lockSeconds", 60);
        ReflectionTestUtils.setField(worker, "maxRetries", 3);
        ReflectionTestUtils.setField(worker, "baseDelaySeconds", 1);

        WorkflowMdcTestSupport.withLoggingContext("trace-worker-1001", "req-worker-1001", worker::pollOnce);

        assertEquals("trace-worker-1001", seenTraceId.get());
        assertEquals("req-worker-1001", seenRequestId.get());
        assertEquals(PublishTaskStatus.AWAITING_CONFIRMATION, store.listPublishTasks(draftId).get(0).getStatus());
        assertNull(WorkflowLogContext.currentTraceId());
        assertNull(WorkflowLogContext.currentRequestId());
    }

    private Long seedRunnableTask(InMemoryWorkflowStore store) {
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

        store.insertSnapshot(ContentSnapshot.builder()
                .draftId(draft.getId())
                .publishedVersion(2)
                .sourceDraftVersion(3)
                .title(draft.getTitle())
                .summary(draft.getSummary())
                .body(draft.getBody())
                .operator("editor")
                .rollback(false)
                .publishedAt(now)
                .build());

        store.insertPublishTasks(List.of(PublishTask.builder()
                .draftId(draft.getId())
                .publishedVersion(2)
                .taskType(PublishTaskType.REFRESH_SEARCH_INDEX)
                .status(PublishTaskStatus.PENDING)
                .retryTimes(0)
                .createdAt(now)
                .updatedAt(now)
                .build()));
        return draft.getId();
    }
}

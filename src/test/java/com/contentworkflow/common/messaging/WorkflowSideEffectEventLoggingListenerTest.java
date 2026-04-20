package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;
import com.contentworkflow.workflow.application.task.PublishTaskProgressService;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowSideEffectEventLoggingListenerTest {

    private InMemoryWorkflowStore store;
    private AtomicInteger readModelGatewayCalls;
    private WorkflowSideEffectEventLoggingListener listener;
    private ObjectMapper objectMapper;
    private Long seededTaskId;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        prepareDraftAndTaskFixture();
        objectMapper = new ObjectMapper().findAndRegisterModules();

        readModelGatewayCalls = new AtomicInteger();
        PublishTaskProgressService progressService = new PublishTaskProgressService(store, event -> {
        });
        WorkflowSideEffectConsumerService consumerService = new WorkflowSideEffectConsumerService(
                payload -> {
                },
                payload -> readModelGatewayCalls.incrementAndGet(),
                payload -> {
                },
                progressService
        );

        listener = new WorkflowSideEffectEventLoggingListener(
                objectMapper,
                new WorkflowMessageDeduplicationGuard(new ConcurrentMapCacheManager("cpw:mq:consumed:message")),
                consumerService
        );
    }

    @Test
    void duplicate_message_should_only_be_consumed_once() throws Exception {
        PublishTaskEventFactory.ReadModelSyncRequestedPayload payload =
                new PublishTaskEventFactory.ReadModelSyncRequestedPayload(
                        seededTaskId,
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
        String json = objectMapper.writeValueAsString(payload);

        listener.onReadModelSyncRequested(json, "msg-1", "DOWNSTREAM_READ_MODEL_SYNC_REQUESTED", Map.of());
        listener.onReadModelSyncRequested(json, "msg-1", "DOWNSTREAM_READ_MODEL_SYNC_REQUESTED", Map.of());

        assertEquals(1, readModelGatewayCalls.get());
        long confirmedLogs = store.listPublishLogs(1L).stream()
                .filter(log -> "MQ_READ_MODEL_CONFIRMED".equals(log.getActionType()))
                .count();
        assertEquals(1L, confirmedLogs);

        PublishTask task = store.listPublishTasks(1L).stream().findFirst().orElseThrow();
        assertEquals(PublishTaskStatus.SUCCESS, task.getStatus());
        assertEquals(WorkflowStatus.PUBLISHED, store.findDraftById(1L).orElseThrow().getStatus());
    }

    private void prepareDraftAndTaskFixture() {
        ContentDraft draft = ContentDraft.builder()
                .bizNo("CPW-1")
                .title("title")
                .summary("summary")
                .body("body")
                .draftVersion(3)
                .publishedVersion(2)
                .status(WorkflowStatus.PUBLISHING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        draft = store.insertDraft(draft);

        PublishTask task = PublishTask.builder()
                .draftId(draft.getId())
                .publishedVersion(2)
                .taskType(PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL)
                .status(PublishTaskStatus.AWAITING_CONFIRMATION)
                .retryTimes(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        seededTaskId = store.insertPublishTasks(List.of(task)).get(0).getId();
    }
}

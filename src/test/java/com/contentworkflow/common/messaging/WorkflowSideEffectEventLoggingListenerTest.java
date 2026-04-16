package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
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

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class WorkflowSideEffectEventLoggingListenerTest {

    private WorkflowStore store;
    private AtomicInteger readModelGatewayCalls;
    private WorkflowSideEffectEventLoggingListener listener;
    private ObjectMapper objectMapper;

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        seedDraft();
        objectMapper = new ObjectMapper().findAndRegisterModules();

        readModelGatewayCalls = new AtomicInteger();
        WorkflowSideEffectConsumerService consumerService = new WorkflowSideEffectConsumerService(
                store,
                payload -> {
                },
                payload -> readModelGatewayCalls.incrementAndGet(),
                payload -> {
                }
        );

        listener = new WorkflowSideEffectEventLoggingListener(
                objectMapper,
                new WorkflowMessageDeduplicationGuard(new ConcurrentMapCacheManager("cpw:mq:consumed:message")),
                consumerService
        );
    }

    /**
     * 处理 duplicate_message_should_only_be_consumed_once 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void duplicate_message_should_only_be_consumed_once() throws Exception {
        PublishTaskEventFactory.ReadModelSyncRequestedPayload payload =
                new PublishTaskEventFactory.ReadModelSyncRequestedPayload(
                        11L,
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
        long acceptedLogs = store.listPublishLogs(1L).stream()
                .filter(log -> "MQ_READ_MODEL_ACCEPTED".equals(log.getActionType()))
                .count();
        assertEquals(1L, acceptedLogs);
    }

    /**
     * 处理 seed draft 相关逻辑，并返回对应的执行结果。
     */

    private void seedDraft() {
        ContentDraft draft = ContentDraft.builder()
                .id(1L)
                .bizNo("CPW-1")
                .title("title")
                .summary("summary")
                .body("body")
                .draftVersion(1)
                .publishedVersion(1)
                .status(WorkflowStatus.PUBLISHED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        store.insertDraft(draft);
    }
}

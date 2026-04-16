package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.messaging.outbox.OutboxEventEntity;
import com.contentworkflow.common.messaging.outbox.OutboxEventRepository;
import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.testing.BusinessExceptionAssertions;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.vo.ManualRecoveryResponse;
import com.contentworkflow.workflow.interfaces.vo.RecoverableOutboxEventResponse;
import com.contentworkflow.workflow.interfaces.vo.RecoverablePublishTaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class WorkflowRecoveryServiceTest {

    private final WorkflowOperatorIdentity operator = new WorkflowOperatorIdentity("2001", "ops-alice", WorkflowRole.OPERATOR);

    private InMemoryWorkflowStore store;
    private OutboxEventRepository outboxEventRepository;
    private WorkflowRecoveryService service;

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        outboxEventRepository = mock(OutboxEventRepository.class);
        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new WorkflowRecoveryService(
                store,
                outboxEventRepository,
                new ConcurrentMapCacheManager()
        );
    }

    /**
     * 处理 retry publish task_should reset recoverable task and resume draft 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void retryPublishTask_shouldResetRecoverableTaskAndResumeDraft() {
        ContentDraft draft = insertDraft(2, WorkflowStatus.PUBLISH_FAILED);
        PublishTask task = insertTask(draft.getId(), 2, PublishTaskType.REFRESH_SEARCH_INDEX, PublishTaskStatus.DEAD, 5);

        ManualRecoveryResponse response = service.retryPublishTask(draft.getId(), task.getId(), "manual retry", operator);

        PublishTask updated = store.listPublishTasks(draft.getId()).stream()
                .filter(item -> item.getId().equals(task.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(PublishTaskStatus.PENDING, updated.getStatus());
        assertEquals(0, updated.getRetryTimes());
        assertNull(updated.getErrorMessage());
        assertEquals(WorkflowStatus.PUBLISHING, store.findDraftById(draft.getId()).orElseThrow().getStatus());
        assertEquals("DEAD", response.beforeStatus());
        assertEquals("PENDING", response.afterStatus());
    }

    /**
     * 处理 retry publish task_should reject stale published version 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void retryPublishTask_shouldRejectStalePublishedVersion() {
        ContentDraft draft = insertDraft(3, WorkflowStatus.PUBLISH_FAILED);
        PublishTask staleTask = insertTask(draft.getId(), 2, PublishTaskType.REFRESH_SEARCH_INDEX, PublishTaskStatus.FAILED, 2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retryPublishTask(draft.getId(), staleTask.getId(), null, operator));

        BusinessExceptionAssertions.assertCode(ex, "STALE_PUBLISH_TASK");
    }

    /**
     * 处理 retry current version publish tasks_should only reset recoverable current tasks 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void retryCurrentVersionPublishTasks_shouldOnlyResetRecoverableCurrentTasks() {
        ContentDraft draft = insertDraft(4, WorkflowStatus.PUBLISH_FAILED);
        PublishTask failedTask = insertTask(draft.getId(), 4, PublishTaskType.REFRESH_SEARCH_INDEX, PublishTaskStatus.FAILED, 2);
        PublishTask deadTask = insertTask(draft.getId(), 4, PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL, PublishTaskStatus.DEAD, 5);
        PublishTask successTask = insertTask(draft.getId(), 4, PublishTaskType.SEND_PUBLISH_NOTIFICATION, PublishTaskStatus.SUCCESS, 1);
        PublishTask staleTask = insertTask(draft.getId(), 3, PublishTaskType.REFRESH_SEARCH_INDEX, PublishTaskStatus.DEAD, 6);

        List<ManualRecoveryResponse> responses = service.retryCurrentVersionPublishTasks(draft.getId(), "batch", operator);

        assertEquals(2, responses.size());
        assertEquals(PublishTaskStatus.PENDING, taskById(draft.getId(), failedTask.getId()).getStatus());
        assertEquals(PublishTaskStatus.PENDING, taskById(draft.getId(), deadTask.getId()).getStatus());
        assertEquals(PublishTaskStatus.SUCCESS, taskById(draft.getId(), successTask.getId()).getStatus());
        assertEquals(PublishTaskStatus.DEAD, taskById(draft.getId(), staleTask.getId()).getStatus());
        assertEquals(WorkflowStatus.PUBLISHING, store.findDraftById(draft.getId()).orElseThrow().getStatus());
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     */

    @Test
    void listRecoverablePublishTasks_shouldMarkStaleTasksAsNotActionable() {
        ContentDraft draft = insertDraft(2, WorkflowStatus.PUBLISH_FAILED);
        PublishTask currentTask = insertTask(draft.getId(), 2, PublishTaskType.REFRESH_SEARCH_INDEX, PublishTaskStatus.FAILED, 1);
        PublishTask staleTask = insertTask(draft.getId(), 1, PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL, PublishTaskStatus.DEAD, 4);

        List<RecoverablePublishTaskResponse> items = service.listRecoverablePublishTasks(draft.getId(), null);

        RecoverablePublishTaskResponse current = items.stream()
                .filter(item -> item.id().equals(currentTask.getId()))
                .findFirst()
                .orElseThrow();
        RecoverablePublishTaskResponse stale = items.stream()
                .filter(item -> item.id().equals(staleTask.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(current.actionable());
        assertFalse(current.staleVersion());
        assertFalse(stale.actionable());
        assertTrue(stale.staleVersion());
    }

    /**
     * 处理 retry outbox event_should reset failed event to new 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void retryOutboxEvent_shouldResetFailedEventToNew() {
        ContentDraft draft = insertDraft(1, WorkflowStatus.PUBLISHING);
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId("evt-1");
        event.setEventType("SEARCH_INDEX_REFRESH_REQUESTED");
        event.setAggregateType("content_draft");
        event.setAggregateId(String.valueOf(draft.getId()));
        event.setStatus(OutboxEventStatus.FAILED);
        event.setAttempt(3);
        event.setErrorMessage("broken");
        event.setNextRetryAt(LocalDateTime.now().minusMinutes(1));

        when(outboxEventRepository.findById(99L)).thenReturn(java.util.Optional.of(event));

        ManualRecoveryResponse response = service.retryOutboxEvent(99L, "reset relay", operator);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        assertEquals(OutboxEventStatus.NEW, captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getAttempt());
        assertNull(captor.getValue().getErrorMessage());
        assertEquals("FAILED", response.beforeStatus());
        assertEquals("NEW", response.afterStatus());
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     */

    @Test
    void listRecoverableOutboxEvents_shouldMapRepositoryRows() {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId("evt-2");
        event.setEventType("PUBLISH_NOTIFICATION_REQUESTED");
        event.setAggregateType("content_draft");
        event.setAggregateId("12");
        event.setAggregateVersion(8);
        event.setStatus(OutboxEventStatus.DEAD);
        event.setAttempt(5);
        event.setErrorMessage("dead");
        event.setNextRetryAt(LocalDateTime.now());
        when(outboxEventRepository.findByStatusIn(any(), any()))
                .thenReturn(List.of(event));

        List<RecoverableOutboxEventResponse> items = service.listRecoverableOutboxEvents(null, null, 20);

        assertEquals(1, items.size());
        assertEquals("evt-2", items.get(0).eventId());
        assertTrue(items.get(0).recoverable());
    }

    /**
     * 处理 insert draft 相关逻辑，并返回对应的执行结果。
     *
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @param status 状态值
     * @return 方法处理后的结果对象
     */

    private ContentDraft insertDraft(int publishedVersion, WorkflowStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return store.insertDraft(ContentDraft.builder()
                .title("title")
                .summary("summary")
                .body("body")
                .draftVersion(10)
                .publishedVersion(publishedVersion)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /**
     * 处理 insert task 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @param taskType 参数 taskType 对应的业务输入值
     * @param status 状态值
     * @param retryTimes 参数 retryTimes 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private PublishTask insertTask(Long draftId,
                                   int publishedVersion,
                                   PublishTaskType taskType,
                                   PublishTaskStatus status,
                                   int retryTimes) {
        LocalDateTime now = LocalDateTime.now();
        PublishTask task = PublishTask.builder()
                .draftId(draftId)
                .publishedVersion(publishedVersion)
                .taskType(taskType)
                .status(status)
                .retryTimes(retryTimes)
                .errorMessage("err")
                .nextRunAt(now.minusMinutes(1))
                .createdAt(now)
                .updatedAt(now)
                .build();
        return store.insertPublishTasks(List.of(task)).get(0);
    }

    /**
     * 处理 task by id 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @param taskId 相关业务对象的唯一标识
     * @return 方法处理后的结果对象
     */

    private PublishTask taskById(Long draftId, Long taskId) {
        return store.listPublishTasks(draftId).stream()
                .filter(item -> item.getId().equals(taskId))
                .findFirst()
                .orElseThrow();
    }
}

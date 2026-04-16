package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.common.messaging.WorkflowEventTypes;
import com.contentworkflow.workflow.application.ContentWorkflowService;
import com.contentworkflow.workflow.application.InMemoryContentWorkflowService;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.OfflineRequest;
import com.contentworkflow.workflow.interfaces.dto.PublishRequest;
import com.contentworkflow.workflow.interfaces.dto.ReviewDecisionRequest;
import com.contentworkflow.workflow.interfaces.dto.SubmitReviewRequest;
import com.contentworkflow.workflow.interfaces.dto.UpdateDraftRequest;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class PublishTaskWorkerEventDispatchTest {

    private WorkflowStore store;
    private ContentWorkflowService service;
    private RecordingWorkflowEventPublisher eventPublisher;
    private PublishTaskWorker worker;

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        service = new InMemoryContentWorkflowService(store);
        eventPublisher = new RecordingWorkflowEventPublisher();

        DefaultPublishTaskHandlers handlers = new DefaultPublishTaskHandlers();
        worker = new PublishTaskWorker(
                store,
                List.of(
                        handlers.refreshSearchIndexTaskHandler(),
                        handlers.syncDownstreamReadModelTaskHandler(),
                        handlers.sendPublishNotificationTaskHandler()
                ),
                eventPublisher
        );
        ReflectionTestUtils.setField(worker, "enabled", true);
        ReflectionTestUtils.setField(worker, "batchSize", 20);
        ReflectionTestUtils.setField(worker, "lockSeconds", 60);
        ReflectionTestUtils.setField(worker, "maxRetries", 5);
        ReflectionTestUtils.setField(worker, "baseDelaySeconds", 1);
    }

    /**
     * 处理 first_publish_should_dispatch_all_side_effect_events_and_finalize_draft 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void first_publish_should_dispatch_all_side_effect_events_and_finalize_draft() {
        ContentDraftResponse draft = createApprovedDraft("title-v1", "summary-v1", "body-v1");

        service.publish(draft.id(), new PublishRequest("publish v1", null));
        worker.pollOnce();

        assertEquals(WorkflowStatus.PUBLISHED, store.findDraftById(draft.id()).orElseThrow().getStatus());

        List<PublishTask> tasks = tasksOfVersion(draft.id(), 1);
        assertEquals(3, tasks.size());
        assertTrue(tasks.stream().allMatch(task -> task.getStatus() == PublishTaskStatus.SUCCESS));

        Set<String> eventTypes = currentEventTypes();
        assertEquals(Set.of(
                WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED,
                WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED,
                WorkflowEventTypes.PUBLISH_NOTIFICATION_REQUESTED,
                WorkflowEventTypes.CONTENT_PUBLISHED
        ), eventTypes);

        WorkflowEvent searchIndexEvent = findEvent(WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED);
        assertInstanceOf(
                PublishTaskEventFactory.SearchIndexRefreshRequestedPayload.class,
                searchIndexEvent.payload()
        );
    }

    /**
     * 处理 format_only_change_should_only_dispatch_read_model_sync_event 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void format_only_change_should_only_dispatch_read_model_sync_event() {
        ContentDraftResponse draft = createApprovedDraft("title-v1", "summary-v1", "body-v1");
        publishAndFinalize(draft.id(), "publish v1");
        eventPublisher.clear();

        service.offline(draft.id(), new OfflineRequest("offline for formatting"));
        service.updateDraft(draft.id(), new UpdateDraftRequest("title-v1", "summary-v1", "body-v1   "));
        approveDraft(draft.id());

        service.publish(draft.id(), new PublishRequest("publish format-only v2", null));
        worker.pollOnce();

        Set<String> eventTypes = currentEventTypes();
        assertEquals(Set.of(
                WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED,
                WorkflowEventTypes.CONTENT_PUBLISHED
        ), eventTypes);

        List<PublishTask> tasks = tasksOfVersion(draft.id(), 2);
        assertEquals(1, tasks.size());
        assertEquals(PublishTaskStatus.SUCCESS, tasks.get(0).getStatus());
    }

    /**
     * 处理 metadata_change_should_dispatch_search_sync_and_notification_events 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void metadata_change_should_dispatch_search_sync_and_notification_events() {
        ContentDraftResponse draft = createApprovedDraft("title-v1", "summary-v1", "body-v1");
        publishAndFinalize(draft.id(), "publish v1");
        eventPublisher.clear();

        service.offline(draft.id(), new OfflineRequest("offline for metadata"));
        service.updateDraft(draft.id(), new UpdateDraftRequest("title-v2", "summary-v1", "body-v1"));
        approveDraft(draft.id());

        service.publish(draft.id(), new PublishRequest("publish metadata v2", null));
        worker.pollOnce();

        Set<String> eventTypes = currentEventTypes();
        assertEquals(Set.of(
                WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED,
                WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED,
                WorkflowEventTypes.PUBLISH_NOTIFICATION_REQUESTED,
                WorkflowEventTypes.CONTENT_PUBLISHED
        ), eventTypes);
    }

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。
     *
     * @param title 参数 title 对应的业务输入值
     * @param summary 参数 summary 对应的业务输入值
     * @param body 参数 body 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private ContentDraftResponse createApprovedDraft(String title, String summary, String body) {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest(title, summary, body));
        approveDraft(draft.id());
        return draft;
    }

    /**
     * 执行通过操作，并推进后续处理流程。
     *
     * @param draftId 草稿唯一标识
     */

    private void approveDraft(Long draftId) {
        service.submitReview(draftId, new SubmitReviewRequest("editor", "ready"));
        service.review(draftId, new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @param draftId 草稿唯一标识
     * @param remark 参数 remark 对应的业务输入值
     */

    private void publishAndFinalize(Long draftId, String remark) {
        service.publish(draftId, new PublishRequest(remark, null));
        worker.pollOnce();
        ContentDraft draft = store.findDraftById(draftId).orElseThrow();
        assertEquals(WorkflowStatus.PUBLISHED, draft.getStatus());
    }

    /**
     * 处理 tasks of version 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @return 符合条件的结果集合
     */

    private List<PublishTask> tasksOfVersion(Long draftId, int publishedVersion) {
        return store.listPublishTasks(draftId).stream()
                .filter(task -> Integer.valueOf(publishedVersion).equals(task.getPublishedVersion()))
                .toList();
    }

    /**
     * 处理 current event types 相关逻辑，并返回对应的执行结果。
     *
     * @return 符合条件的结果集合
     */

    private Set<String> currentEventTypes() {
        return eventPublisher.events().stream()
                .map(WorkflowEvent::eventType)
                .collect(Collectors.toSet());
    }

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param eventType 参数 eventType 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private WorkflowEvent findEvent(String eventType) {
        return eventPublisher.events().stream()
                .filter(event -> eventType.equals(event.eventType()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 发布器组件，用于封装事件构建、消息投递或 outbox 发布逻辑。
     */

    private static class RecordingWorkflowEventPublisher implements WorkflowEventPublisher {

        private final List<WorkflowEvent> events = new CopyOnWriteArrayList<>();

        /**
         * 触发发布流程，并返回发布动作对应的处理结果。
         *
         * @param event 事件对象
         */

        @Override
        public void publish(WorkflowEvent event) {
            events.add(event);
        }

        /**
         * 处理 events 相关逻辑，并返回对应的执行结果。
         *
         * @return 符合条件的结果集合
         */

        public List<WorkflowEvent> events() {
            return List.copyOf(events);
        }

        /**
         * 处理 clear 相关逻辑，并返回对应的执行结果。
         */

        public void clear() {
            events.clear();
        }
    }
}

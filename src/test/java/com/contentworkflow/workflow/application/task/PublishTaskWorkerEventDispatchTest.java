package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.messaging.WorkflowEvent;
import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.common.messaging.WorkflowEventTypes;
import com.contentworkflow.common.messaging.WorkflowSideEffectConsumerService;
import com.contentworkflow.workflow.application.ContentWorkflowService;
import com.contentworkflow.workflow.application.ContentWorkflowApplicationService;
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

class PublishTaskWorkerEventDispatchTest {

    private WorkflowStore store;
    private ContentWorkflowService service;
    private RecordingWorkflowEventPublisher eventPublisher;
    private PublishTaskWorker worker;
    private WorkflowSideEffectConsumerService consumerService;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        service = new ContentWorkflowApplicationService(store);
        eventPublisher = new RecordingWorkflowEventPublisher();

        PublishTaskProgressService taskProgressService = new PublishTaskProgressService(store, eventPublisher);
        DefaultPublishTaskHandlers handlers = new DefaultPublishTaskHandlers();
        worker = new PublishTaskWorker(
                store,
                List.of(
                        handlers.refreshSearchIndexTaskHandler(),
                        handlers.syncDownstreamReadModelTaskHandler(),
                        handlers.sendPublishNotificationTaskHandler()
                ),
                eventPublisher,
                taskProgressService
        );
        consumerService = new WorkflowSideEffectConsumerService(
                payload -> {
                },
                payload -> {
                },
                payload -> {
                },
                taskProgressService
        );

        ReflectionTestUtils.setField(worker, "enabled", true);
        ReflectionTestUtils.setField(worker, "batchSize", 20);
        ReflectionTestUtils.setField(worker, "lockSeconds", 60);
        ReflectionTestUtils.setField(worker, "maxRetries", 5);
        ReflectionTestUtils.setField(worker, "baseDelaySeconds", 1);
    }

    @Test
    void first_publish_should_dispatch_all_side_effect_events_and_finalize_draft() {
        ContentDraftResponse draft = createApprovedDraft("title-v1", "summary-v1", "body-v1");

        service.publish(draft.id(), new PublishRequest("publish v1", null));
        worker.pollOnce();

        assertEquals(WorkflowStatus.PUBLISHING, store.findDraftById(draft.id()).orElseThrow().getStatus());
        List<PublishTask> tasks = tasksOfVersion(draft.id(), 1);
        assertEquals(3, tasks.size());
        assertTrue(tasks.stream().allMatch(task -> task.getStatus() == PublishTaskStatus.AWAITING_CONFIRMATION));

        Set<String> dispatchedEventTypes = currentEventTypes();
        assertEquals(Set.of(
                WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED,
                WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED,
                WorkflowEventTypes.PUBLISH_NOTIFICATION_REQUESTED
        ), dispatchedEventTypes);

        WorkflowEvent searchIndexEvent = findEvent(WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED);
        assertInstanceOf(PublishTaskEventFactory.SearchIndexRefreshRequestedPayload.class, searchIndexEvent.payload());

        confirmDispatchedEvents();

        assertEquals(WorkflowStatus.PUBLISHED, store.findDraftById(draft.id()).orElseThrow().getStatus());
        assertTrue(tasksOfVersion(draft.id(), 1).stream().allMatch(task -> task.getStatus() == PublishTaskStatus.SUCCESS));
        assertTrue(currentEventTypes().contains(WorkflowEventTypes.CONTENT_PUBLISHED));
    }

    @Test
    void format_only_change_should_only_dispatch_read_model_sync_event() {
        ContentDraftResponse draft = createApprovedDraft("title-v1", "summary-v1", "body-v1");
        publishAndFinalize(draft.id(), "publish v1");
        eventPublisher.clear();

        service.offline(draft.id(), new OfflineRequest("offline for formatting"));
        service.updateDraft(draft.id(), new UpdateDraftRequest(service.getDraft(draft.id()).version(), "title-v1", "summary-v1", "body-v1   "));
        approveDraft(draft.id());

        service.publish(draft.id(), new PublishRequest("publish format-only v2", null));
        worker.pollOnce();

        Set<String> eventTypes = currentEventTypes();
        assertEquals(Set.of(WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED), eventTypes);

        List<PublishTask> tasks = tasksOfVersion(draft.id(), 2);
        assertEquals(1, tasks.size());
        assertEquals(PublishTaskStatus.AWAITING_CONFIRMATION, tasks.get(0).getStatus());

        confirmDispatchedEvents();
        assertEquals(PublishTaskStatus.SUCCESS, tasksOfVersion(draft.id(), 2).get(0).getStatus());
        assertTrue(currentEventTypes().contains(WorkflowEventTypes.CONTENT_PUBLISHED));
    }

    @Test
    void metadata_change_should_dispatch_search_sync_and_notification_events() {
        ContentDraftResponse draft = createApprovedDraft("title-v1", "summary-v1", "body-v1");
        publishAndFinalize(draft.id(), "publish v1");
        eventPublisher.clear();

        service.offline(draft.id(), new OfflineRequest("offline for metadata"));
        service.updateDraft(draft.id(), new UpdateDraftRequest(service.getDraft(draft.id()).version(), "title-v2", "summary-v1", "body-v1"));
        approveDraft(draft.id());

        service.publish(draft.id(), new PublishRequest("publish metadata v2", null));
        worker.pollOnce();

        Set<String> eventTypes = currentEventTypes();
        assertEquals(Set.of(
                WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED,
                WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED,
                WorkflowEventTypes.PUBLISH_NOTIFICATION_REQUESTED
        ), eventTypes);

        confirmDispatchedEvents();
        assertTrue(currentEventTypes().contains(WorkflowEventTypes.CONTENT_PUBLISHED));
    }

    @Test
    void worker_shouldNotFinalizeDraftWhenStatusAlreadyMovedOutOfPublishing() {
        ContentDraftResponse draft = createApprovedDraft("title-v1", "summary-v1", "body-v1");

        service.publish(draft.id(), new PublishRequest("publish v1", null));
        ContentDraft publishingDraft = store.findDraftById(draft.id()).orElseThrow();
        publishingDraft.setStatus(WorkflowStatus.PUBLISH_FAILED);
        store.updateDraft(publishingDraft, java.util.EnumSet.of(WorkflowStatus.PUBLISHING));

        worker.pollOnce();
        confirmDispatchedEvents();

        ContentDraft persisted = store.findDraftById(draft.id()).orElseThrow();
        assertEquals(WorkflowStatus.PUBLISH_FAILED, persisted.getStatus());
        assertTrue(currentEventTypes().contains(WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED));
        assertTrue(!currentEventTypes().contains(WorkflowEventTypes.CONTENT_PUBLISHED));
    }

    private ContentDraftResponse createApprovedDraft(String title, String summary, String body) {
        ContentDraftResponse draft = service.createDraft(new CreateDraftRequest(title, summary, body));
        approveDraft(draft.id());
        return draft;
    }

    private void approveDraft(Long draftId) {
        service.submitReview(draftId, new SubmitReviewRequest("editor", "ready"));
        service.review(draftId, new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));
    }

    private void publishAndFinalize(Long draftId, String remark) {
        service.publish(draftId, new PublishRequest(remark, null));
        worker.pollOnce();
        confirmDispatchedEvents();
        ContentDraft draft = store.findDraftById(draftId).orElseThrow();
        assertEquals(WorkflowStatus.PUBLISHED, draft.getStatus());
    }

    private void confirmDispatchedEvents() {
        for (WorkflowEvent event : List.copyOf(eventPublisher.events())) {
            switch (event.eventType()) {
                case WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED ->
                        consumerService.acceptSearchIndexRefresh("msg-search-" + event.aggregateId(),
                                (PublishTaskEventFactory.SearchIndexRefreshRequestedPayload) event.payload());
                case WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED ->
                        consumerService.acceptReadModelSync("msg-read-" + event.aggregateId(),
                                (PublishTaskEventFactory.ReadModelSyncRequestedPayload) event.payload());
                case WorkflowEventTypes.PUBLISH_NOTIFICATION_REQUESTED ->
                        consumerService.acceptPublishNotification("msg-notify-" + event.aggregateId(),
                                (PublishTaskEventFactory.PublishNotificationRequestedPayload) event.payload());
                default -> {
                }
            }
        }
    }

    private List<PublishTask> tasksOfVersion(Long draftId, int publishedVersion) {
        return store.listPublishTasks(draftId).stream()
                .filter(task -> Integer.valueOf(publishedVersion).equals(task.getPublishedVersion()))
                .toList();
    }

    private Set<String> currentEventTypes() {
        return eventPublisher.events().stream()
                .map(WorkflowEvent::eventType)
                .collect(Collectors.toSet());
    }

    private WorkflowEvent findEvent(String eventType) {
        return eventPublisher.events().stream()
                .filter(event -> eventType.equals(event.eventType()))
                .findFirst()
                .orElseThrow();
    }

    private static class RecordingWorkflowEventPublisher implements WorkflowEventPublisher {

        private final List<WorkflowEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(WorkflowEvent event) {
            events.add(event);
        }

        public List<WorkflowEvent> events() {
            return List.copyOf(events);
        }

        public void clear() {
            events.clear();
        }
    }
}

package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.store.WorkflowAuditLogFactory;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Demo consumer-side application service.
 */
@Service
public class WorkflowSideEffectConsumerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSideEffectConsumerService.class);

    private final WorkflowStore workflowStore;
    private final SearchIndexRefreshGateway searchIndexRefreshGateway;
    private final ReadModelSyncGateway readModelSyncGateway;
    private final PublishNotificationGateway publishNotificationGateway;

    public WorkflowSideEffectConsumerService(WorkflowStore workflowStore,
                                             SearchIndexRefreshGateway searchIndexRefreshGateway,
                                             ReadModelSyncGateway readModelSyncGateway,
                                             PublishNotificationGateway publishNotificationGateway) {
        this.workflowStore = workflowStore;
        this.searchIndexRefreshGateway = searchIndexRefreshGateway;
        this.readModelSyncGateway = readModelSyncGateway;
        this.publishNotificationGateway = publishNotificationGateway;
    }

    @Transactional
    public void acceptSearchIndexRefresh(String messageId,
                                         PublishTaskEventFactory.SearchIndexRefreshRequestedPayload payload) {
        searchIndexRefreshGateway.refresh(payload);
        log.info("accept search-index refresh messageId={} draftId={} version={}",
                messageId, payload.draftId(), payload.publishedVersion());
        appendAuditLog(buildTask(payload.taskId(), payload.draftId(), payload.publishedVersion()),
                "MQ_SEARCH_INDEX_ACCEPTED",
                payload.operator(),
                "messageId=" + messageId + ";version=" + payload.publishedVersion() + ";snapshotId=" + payload.snapshotId());
    }

    @Transactional
    public void acceptReadModelSync(String messageId,
                                    PublishTaskEventFactory.ReadModelSyncRequestedPayload payload) {
        readModelSyncGateway.sync(payload);
        log.info("accept read-model sync messageId={} draftId={} version={}",
                messageId, payload.draftId(), payload.publishedVersion());
        appendAuditLog(buildTask(payload.taskId(), payload.draftId(), payload.publishedVersion()),
                "MQ_READ_MODEL_ACCEPTED",
                payload.operator(),
                "messageId=" + messageId + ";version=" + payload.publishedVersion() + ";snapshotId=" + payload.snapshotId());
    }

    @Transactional
    public void acceptPublishNotification(String messageId,
                                          PublishTaskEventFactory.PublishNotificationRequestedPayload payload) {
        publishNotificationGateway.notifyPublish(payload);
        log.info("accept publish-notification messageId={} draftId={} version={}",
                messageId, payload.draftId(), payload.publishedVersion());
        appendAuditLog(buildTask(payload.taskId(), payload.draftId(), payload.publishedVersion()),
                "MQ_NOTIFY_ACCEPTED",
                payload.operator(),
                "messageId=" + messageId + ";version=" + payload.publishedVersion() + ";snapshotId=" + payload.snapshotId());
    }

    private void appendAuditLog(PublishTask task, String actionType, String operator, String remark) {
        if (task == null || task.getDraftId() == null || workflowStore.findDraftById(task.getDraftId()).isEmpty()) {
            return;
        }
        workflowStore.insertPublishLog(WorkflowAuditLogFactory.taskAction(
                        task,
                        actionType,
                        "mq-consumer",
                        operator == null || operator.isBlank() ? "mq-consumer" : operator
                )
                .result(WorkflowAuditResult.SUCCESS)
                .remark(remark)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private PublishTask buildTask(Long taskId, Long draftId, Integer publishedVersion) {
        return PublishTask.builder()
                .id(taskId)
                .draftId(draftId)
                .publishedVersion(publishedVersion)
                .build();
    }
}

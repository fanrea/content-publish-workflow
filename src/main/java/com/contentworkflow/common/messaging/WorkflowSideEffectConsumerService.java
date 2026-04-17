package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;
import com.contentworkflow.workflow.application.task.PublishTaskProgressService;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumes side-effect requests and turns downstream success into task
 * confirmation.
 */
@Service
public class WorkflowSideEffectConsumerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSideEffectConsumerService.class);

    private final SearchIndexRefreshGateway searchIndexRefreshGateway;
    private final ReadModelSyncGateway readModelSyncGateway;
    private final PublishNotificationGateway publishNotificationGateway;
    private final PublishTaskProgressService taskProgressService;

    public WorkflowSideEffectConsumerService(SearchIndexRefreshGateway searchIndexRefreshGateway,
                                             ReadModelSyncGateway readModelSyncGateway,
                                             PublishNotificationGateway publishNotificationGateway,
                                             PublishTaskProgressService taskProgressService) {
        this.searchIndexRefreshGateway = searchIndexRefreshGateway;
        this.readModelSyncGateway = readModelSyncGateway;
        this.publishNotificationGateway = publishNotificationGateway;
        this.taskProgressService = taskProgressService;
    }

    @Transactional
    public void acceptSearchIndexRefresh(String messageId,
                                         PublishTaskEventFactory.SearchIndexRefreshRequestedPayload payload) {
        searchIndexRefreshGateway.refresh(payload);
        log.info("confirm search-index refresh messageId={} draftId={} version={}",
                messageId, payload.draftId(), payload.publishedVersion());
        taskProgressService.confirmTaskSuccess(
                payload.draftId(),
                payload.taskId(),
                payload.publishedVersion(),
                PublishTaskType.REFRESH_SEARCH_INDEX,
                normalizeOperator(payload.operator()),
                "MQ_SEARCH_INDEX_CONFIRMED",
                "messageId=" + messageId + ";version=" + payload.publishedVersion() + ";snapshotId=" + payload.snapshotId(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void acceptReadModelSync(String messageId,
                                    PublishTaskEventFactory.ReadModelSyncRequestedPayload payload) {
        readModelSyncGateway.sync(payload);
        log.info("confirm read-model sync messageId={} draftId={} version={}",
                messageId, payload.draftId(), payload.publishedVersion());
        taskProgressService.confirmTaskSuccess(
                payload.draftId(),
                payload.taskId(),
                payload.publishedVersion(),
                PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL,
                normalizeOperator(payload.operator()),
                "MQ_READ_MODEL_CONFIRMED",
                "messageId=" + messageId + ";version=" + payload.publishedVersion() + ";snapshotId=" + payload.snapshotId(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void acceptPublishNotification(String messageId,
                                          PublishTaskEventFactory.PublishNotificationRequestedPayload payload) {
        publishNotificationGateway.notifyPublish(payload);
        log.info("confirm publish-notification messageId={} draftId={} version={}",
                messageId, payload.draftId(), payload.publishedVersion());
        taskProgressService.confirmTaskSuccess(
                payload.draftId(),
                payload.taskId(),
                payload.publishedVersion(),
                PublishTaskType.SEND_PUBLISH_NOTIFICATION,
                normalizeOperator(payload.operator()),
                "MQ_NOTIFY_CONFIRMED",
                "messageId=" + messageId + ";version=" + payload.publishedVersion() + ";snapshotId=" + payload.snapshotId(),
                LocalDateTime.now()
        );
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "mq-consumer" : operator;
    }
}

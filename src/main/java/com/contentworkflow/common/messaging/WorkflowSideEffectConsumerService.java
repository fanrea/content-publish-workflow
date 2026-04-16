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
 * 应用服务实现，负责组织领域对象、存储组件与外部能力完成业务流程编排。
 */
@Service
public class WorkflowSideEffectConsumerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSideEffectConsumerService.class);

    private final WorkflowStore workflowStore;
    private final SearchIndexRefreshGateway searchIndexRefreshGateway;
    private final ReadModelSyncGateway readModelSyncGateway;
    private final PublishNotificationGateway publishNotificationGateway;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param workflowStore 参数 workflowStore 对应的业务输入值
     * @param searchIndexRefreshGateway 参数 searchIndexRefreshGateway 对应的业务输入值
     * @param readModelSyncGateway 参数 readModelSyncGateway 对应的业务输入值
     * @param publishNotificationGateway 参数 publishNotificationGateway 对应的业务输入值
     */

    public WorkflowSideEffectConsumerService(WorkflowStore workflowStore,
                                             SearchIndexRefreshGateway searchIndexRefreshGateway,
                                             ReadModelSyncGateway readModelSyncGateway,
                                             PublishNotificationGateway publishNotificationGateway) {
        this.workflowStore = workflowStore;
        this.searchIndexRefreshGateway = searchIndexRefreshGateway;
        this.readModelSyncGateway = readModelSyncGateway;
        this.publishNotificationGateway = publishNotificationGateway;
    }

    /**
     * 处理 accept search index refresh 相关逻辑，并返回对应的执行结果。
     *
     * @param messageId 相关业务对象的唯一标识
     * @param payload 参数 payload 对应的业务输入值
     */

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

    /**
     * 处理 accept read model sync 相关逻辑，并返回对应的执行结果。
     *
     * @param messageId 相关业务对象的唯一标识
     * @param payload 参数 payload 对应的业务输入值
     */

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

    /**
     * 处理 accept publish notification 相关逻辑，并返回对应的执行结果。
     *
     * @param messageId 相关业务对象的唯一标识
     * @param payload 参数 payload 对应的业务输入值
     */

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

    /**
     * 处理 append audit log 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param task 任务对象
     * @param actionType 参数 actionType 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @param remark 参数 remark 对应的业务输入值
     */

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

    /**
     * 构建当前场景所需的结果对象或配置内容。
     *
     * @param taskId 相关业务对象的唯一标识
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private PublishTask buildTask(Long taskId, Long draftId, Integer publishedVersion) {
        return PublishTask.builder()
                .id(taskId)
                .draftId(draftId)
                .publishedVersion(publishedVersion)
                .build();
    }
}

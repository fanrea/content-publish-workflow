package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;

/**
 * 网关抽象，用于封装与外部系统、消息通道或副作用执行端之间的交互。
 */
public interface ReadModelSyncGateway {

    /**
     * 同步相关数据状态，保持不同系统或模型之间的一致性。
     *
     * @param payload 参数 payload 对应的业务输入值
     */

    void sync(PublishTaskEventFactory.ReadModelSyncRequestedPayload payload);
}

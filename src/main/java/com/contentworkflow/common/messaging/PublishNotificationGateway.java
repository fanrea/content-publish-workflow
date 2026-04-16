package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;

/**
 * 网关抽象，用于封装与外部系统、消息通道或副作用执行端之间的交互。
 */
public interface PublishNotificationGateway {

    /**
     * 处理 notify publish 相关逻辑，并返回对应的执行结果。
     *
     * @param payload 参数 payload 对应的业务输入值
     */

    void notifyPublish(PublishTaskEventFactory.PublishNotificationRequestedPayload payload);
}

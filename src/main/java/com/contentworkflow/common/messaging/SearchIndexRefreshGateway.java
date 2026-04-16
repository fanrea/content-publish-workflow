package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;

/**
 * 网关抽象，用于封装与外部系统、消息通道或副作用执行端之间的交互。
 */
public interface SearchIndexRefreshGateway {

    /**
     * 刷新相关状态或缓存数据，确保后续读取到最新结果。
     *
     * @param payload 参数 payload 对应的业务输入值
     */

    void refresh(PublishTaskEventFactory.SearchIndexRefreshRequestedPayload payload);
}

package com.contentworkflow.common.messaging.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
@Component
public class JpaOutboxEnqueuer implements OutboxEnqueuer {

    private final OutboxEventRepository repo;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param repo 参数 repo 对应的业务输入值
     */

    public JpaOutboxEnqueuer(OutboxEventRepository repo) {
        this.repo = repo;
    }

    /**
     * 处理 enqueue 相关逻辑，并返回对应的执行结果。
     *
     * @param event 事件对象
     */

    @Override
    @Transactional
    public void enqueue(OutboxEventEntity event) {
        repo.save(event);
    }
}


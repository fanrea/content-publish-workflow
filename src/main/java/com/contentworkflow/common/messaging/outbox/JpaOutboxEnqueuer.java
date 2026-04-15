package com.contentworkflow.common.messaging.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 默认 outbox 入队实现：使用 JPA 将事件持久化到 outbox 表。
 *
 * <p>关键点：该写入应当与主业务数据更新处于同一个事务中，从而做到“要么都提交，要么都回滚”。</p>
 */
@Component
public class JpaOutboxEnqueuer implements OutboxEnqueuer {

    private final OutboxEventRepository repo;

    public JpaOutboxEnqueuer(OutboxEventRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void enqueue(OutboxEventEntity event) {
        repo.save(event);
    }
}


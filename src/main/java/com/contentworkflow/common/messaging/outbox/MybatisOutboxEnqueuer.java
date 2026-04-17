package com.contentworkflow.common.messaging.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MybatisOutboxEnqueuer implements OutboxEnqueuer {

    private final OutboxEventRepository repository;

    public MybatisOutboxEnqueuer(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void enqueue(OutboxEventEntity event) {
        repository.save(event);
    }
}

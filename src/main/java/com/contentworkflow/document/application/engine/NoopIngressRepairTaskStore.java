package com.contentworkflow.document.application.engine;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(IngressRepairTaskStore.class)
public class NoopIngressRepairTaskStore implements IngressRepairTaskStore {

    @Override
    public void saveOrUpdate(IngressRepairTask task) {
        throw new IllegalStateException("operation_repair_task store requires JdbcTemplate");
    }
}

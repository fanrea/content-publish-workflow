package com.contentworkflow.document.application.gc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workflow.gc.compaction.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnMissingBean(DocumentCompactionTaskPublisher.class)
public class NoopDocumentCompactionTaskPublisher implements DocumentCompactionTaskPublisher {

    @Override
    public void publish(DocumentCompactionTask task) {
        // no-op default publisher
    }
}

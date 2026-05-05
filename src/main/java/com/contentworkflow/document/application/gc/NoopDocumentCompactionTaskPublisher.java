package com.contentworkflow.document.application.gc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(DocumentCompactionTaskPublisher.class)
public class NoopDocumentCompactionTaskPublisher implements DocumentCompactionTaskPublisher {

    @Override
    public void publish(DocumentCompactionTask task) {
        // no-op default publisher
    }
}

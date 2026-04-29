package com.contentworkflow.document.application.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(DocumentEventPublisher.class)
public class NoopDocumentEventPublisher implements DocumentEventPublisher {

    @Override
    public void publish(DocumentDomainEvent event) {
        // no-op
    }
}

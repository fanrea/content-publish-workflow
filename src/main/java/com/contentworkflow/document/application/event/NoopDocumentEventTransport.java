package com.contentworkflow.document.application.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(DocumentEventTransport.class)
public class NoopDocumentEventTransport implements DocumentEventTransport {

    @Override
    public void send(DocumentDomainEvent event) {
        // no-op
    }
}

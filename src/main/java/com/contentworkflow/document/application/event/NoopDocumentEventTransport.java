package com.contentworkflow.document.application.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "workflow.event.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDocumentEventTransport implements DocumentEventTransport {

    @Override
    public void send(DocumentDomainEvent event) {
        // no-op
    }
}

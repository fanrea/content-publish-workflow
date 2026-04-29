package com.contentworkflow.document.application.ingress;

import com.contentworkflow.document.application.engine.DocumentOperationIngressHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(DocumentOperationIngressPublisher.class)
public class NoopDocumentOperationIngressPublisher implements DocumentOperationIngressPublisher {

    private final DocumentOperationIngressHandler ingressHandler;

    public NoopDocumentOperationIngressPublisher(ObjectProvider<DocumentOperationIngressHandler> ingressHandlerProvider) {
        this.ingressHandler = ingressHandlerProvider.getIfAvailable();
    }

    @Override
    public void publish(DocumentOperationIngressCommand command) {
        if (ingressHandler != null && command != null) {
            ingressHandler.handle(command);
        }
    }
}

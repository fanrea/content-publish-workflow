package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;

public interface DocumentOperationIngressBus {

    void publish(DocumentOperationIngressCommand command);
}

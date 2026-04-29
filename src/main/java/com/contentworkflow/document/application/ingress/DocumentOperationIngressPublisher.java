package com.contentworkflow.document.application.ingress;

public interface DocumentOperationIngressPublisher {

    void publish(DocumentOperationIngressCommand command) throws Exception;
}

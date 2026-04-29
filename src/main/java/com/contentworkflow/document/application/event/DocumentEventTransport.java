package com.contentworkflow.document.application.event;

public interface DocumentEventTransport {

    void send(DocumentDomainEvent event) throws Exception;
}

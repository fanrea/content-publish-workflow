package com.contentworkflow.document.application.gc;

public interface DocumentCompactionTaskPublisher {

    void publish(DocumentCompactionTask task);
}


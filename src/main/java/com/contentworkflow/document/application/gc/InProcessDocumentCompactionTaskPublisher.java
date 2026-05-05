package com.contentworkflow.document.application.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "workflow.gc.compaction.in-process", name = "enabled", havingValue = "true")
public class InProcessDocumentCompactionTaskPublisher implements DocumentCompactionTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessDocumentCompactionTaskPublisher.class);

    private final DocumentCompactionExecutor compactionExecutor;

    public InProcessDocumentCompactionTaskPublisher(DocumentCompactionExecutor compactionExecutor) {
        this.compactionExecutor = compactionExecutor;
    }

    @Override
    public void publish(DocumentCompactionTask task) {
        if (task == null) {
            return;
        }
        try {
            compactionExecutor.execute(task);
        } catch (Exception ex) {
            log.warn("in-process compaction execute failed, docId={}, trigger={}",
                    task.documentId(),
                    task.trigger(),
                    ex);
        }
    }
}


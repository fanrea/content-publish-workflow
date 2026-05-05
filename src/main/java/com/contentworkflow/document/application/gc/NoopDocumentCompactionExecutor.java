package com.contentworkflow.document.application.gc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(DocumentCompactionExecutor.class)
public class NoopDocumentCompactionExecutor implements DocumentCompactionExecutor {

    @Override
    public void execute(DocumentCompactionTask task) {
        // no-op default executor
    }
}

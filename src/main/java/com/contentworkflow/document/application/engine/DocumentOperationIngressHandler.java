package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DocumentOperationIngressHandler {

    private final CollaborationEngine collaborationEngine;

    public DocumentOperationIngressHandler(CollaborationEngine collaborationEngine) {
        this.collaborationEngine = collaborationEngine;
    }

    public void handle(DocumentOperationIngressCommand command) {
        collaborationEngine.submit(Objects.requireNonNull(command, "command must not be null"));
    }
}

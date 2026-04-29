package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;

public interface CollaborationEngine {

    void submit(DocumentOperationIngressCommand command);
}

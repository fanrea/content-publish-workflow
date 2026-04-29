package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DocumentOperationIngressHandlerTest {

    @Test
    void handle_shouldDelegateToCollaborationEngine() {
        CollaborationEngine collaborationEngine = mock(CollaborationEngine.class);
        DocumentOperationIngressHandler handler = new DocumentOperationIngressHandler(collaborationEngine);
        DocumentOperationIngressCommand command = new DocumentOperationIngressCommand(
                100L,
                1,
                "session-1",
                1L,
                "editor-1",
                "editor-1",
                null,
                LocalDateTime.now()
        );

        handler.handle(command);

        verify(collaborationEngine, times(1)).submit(command);
    }
}

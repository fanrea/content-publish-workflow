package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class InMemoryDocumentOperationIngressBusTest {

    @Test
    void publish_shouldDeliverCommandToEngine() throws Exception {
        CollaborationEngine collaborationEngine = mock(CollaborationEngine.class);
        CountDownLatch delivered = new CountDownLatch(1);
        doAnswer(invocation -> {
            delivered.countDown();
            return null;
        }).when(collaborationEngine).submit(org.mockito.ArgumentMatchers.any(DocumentOperationIngressCommand.class));

        InMemoryDocumentOperationIngressBus bus = new InMemoryDocumentOperationIngressBus(collaborationEngine);
        try {
            bus.publish(new DocumentOperationIngressCommand(
                    100L,
                    1,
                    "session-1",
                    1L,
                    "editor-1",
                    "editor-1",
                    null,
                    LocalDateTime.now()
            ));
            assertThat(delivered.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            bus.close();
        }
    }
}

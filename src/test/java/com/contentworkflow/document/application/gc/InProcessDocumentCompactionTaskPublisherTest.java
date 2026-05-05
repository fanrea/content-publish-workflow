package com.contentworkflow.document.application.gc;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InProcessDocumentCompactionTaskPublisherTest {

    @Test
    void publish_shouldInvokeExecutor() {
        DocumentCompactionExecutor executor = mock(DocumentCompactionExecutor.class);
        InProcessDocumentCompactionTaskPublisher publisher = new InProcessDocumentCompactionTaskPublisher(executor);
        DocumentCompactionTask task = new DocumentCompactionTask(100L, "UPDATE_COUNT", Instant.now());

        publisher.publish(task);

        verify(executor, times(1)).execute(task);
    }

    @Test
    void publish_shouldSwallowExecutorException() {
        DocumentCompactionExecutor executor = mock(DocumentCompactionExecutor.class);
        InProcessDocumentCompactionTaskPublisher publisher = new InProcessDocumentCompactionTaskPublisher(executor);
        DocumentCompactionTask task = new DocumentCompactionTask(100L, "UPDATE_COUNT", Instant.now());
        doThrow(new RuntimeException("execute failed")).when(executor).execute(task);

        assertThatCode(() -> publisher.publish(task)).doesNotThrowAnyException();
    }
}


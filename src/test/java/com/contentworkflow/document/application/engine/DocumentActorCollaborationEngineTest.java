package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.contentworkflow.document.application.realtime.DocumentOperationService;
import com.contentworkflow.document.application.realtime.DocumentRealtimePushService;
import com.contentworkflow.document.domain.entity.CollaborativeDocument;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DocumentActorCollaborationEngineTest {

    @Test
    void submit_shouldProcessCommandsSeriallyForSameDocument() throws Exception {
        DocumentOperationService operationService = mock(DocumentOperationService.class);
        DocumentRealtimePushService pushService = mock(DocumentRealtimePushService.class);
        DocumentActorCollaborationEngine engine = new DocumentActorCollaborationEngine(operationService, pushService, 4);

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(2);

        doAnswer(invocation -> {
            int now = active.incrementAndGet();
            maxActive.updateAndGet(prev -> Math.max(prev, now));
            try {
                Thread.sleep(80);
                Long docId = invocation.getArgument(0);
                Long clientSeq = invocation.getArgument(3);
                return applyResult(docId, clientSeq, false);
            } finally {
                active.decrementAndGet();
                done.countDown();
            }
        }).when(operationService).applyOperation(
                anyLong(),
                anyInt(),
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                any(DocumentWsOperation.class)
        );

        try {
            engine.submit(command(100L, 1L));
            engine.submit(command(100L, 2L));
            assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(maxActive.get()).isEqualTo(1);
        } finally {
            engine.destroy();
        }
    }

    @Test
    void submit_shouldProcessDifferentDocumentsConcurrently() throws Exception {
        DocumentOperationService operationService = mock(DocumentOperationService.class);
        DocumentRealtimePushService pushService = mock(DocumentRealtimePushService.class);
        DocumentActorCollaborationEngine engine = new DocumentActorCollaborationEngine(operationService, pushService, 2);

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);

        doAnswer(invocation -> {
            int now = active.incrementAndGet();
            maxActive.updateAndGet(prev -> Math.max(prev, now));
            started.countDown();
            started.await(1, TimeUnit.SECONDS);
            try {
                Thread.sleep(120);
                Long docId = invocation.getArgument(0);
                Long clientSeq = invocation.getArgument(3);
                return applyResult(docId, clientSeq, false);
            } finally {
                active.decrementAndGet();
                done.countDown();
            }
        }).when(operationService).applyOperation(
                anyLong(),
                anyInt(),
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                any(DocumentWsOperation.class)
        );

        try {
            engine.submit(command(1L, 1L));
            engine.submit(command(2L, 2L));
            assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(maxActive.get()).isGreaterThan(1);
        } finally {
            engine.destroy();
        }
    }

    @Test
    void submit_shouldBroadcastAppliedEventAfterSuccessfulApply() throws Exception {
        DocumentOperationService operationService = mock(DocumentOperationService.class);
        DocumentRealtimePushService pushService = mock(DocumentRealtimePushService.class);
        DocumentActorCollaborationEngine engine = new DocumentActorCollaborationEngine(operationService, pushService, 1);

        CountDownLatch pushed = new CountDownLatch(1);
        doAnswer(invocation -> {
            pushed.countDown();
            return null;
        }).when(pushService).broadcastOperationApplied(any(DocumentOperation.class));

        doAnswer(invocation -> applyResult(invocation.getArgument(0), invocation.getArgument(3), false))
                .when(operationService).applyOperation(
                        anyLong(),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(DocumentWsOperation.class)
                );

        try {
            engine.submit(command(100L, 10L));
            assertThat(pushed.await(2, TimeUnit.SECONDS)).isTrue();
            verify(pushService, times(1)).broadcastOperationApplied(any(DocumentOperation.class));
        } finally {
            engine.destroy();
        }
    }

    private DocumentOperationIngressCommand command(Long docId, Long clientSeq) {
        DocumentWsOperation operation = new DocumentWsOperation();
        operation.setOpType(DocumentOpType.INSERT);
        operation.setPosition(0);
        operation.setLength(0);
        operation.setText("x");
        return new DocumentOperationIngressCommand(
                docId,
                1,
                "session-" + docId,
                clientSeq,
                "editor-1",
                "editor-1",
                operation,
                LocalDateTime.now()
        );
    }

    private DocumentOperationService.ApplyResult applyResult(Long docId, Long clientSeq, boolean duplicated) {
        CollaborativeDocument document = CollaborativeDocument.builder()
                .id(docId)
                .latestRevision(2)
                .content("x")
                .title("doc")
                .build();
        DocumentOperation operation = DocumentOperation.builder()
                .id(clientSeq)
                .documentId(docId)
                .revisionNo(2)
                .baseRevision(1)
                .sessionId("session-" + docId)
                .clientSeq(clientSeq)
                .opType(DocumentOpType.INSERT)
                .opPosition(0)
                .opLength(0)
                .opText("x")
                .editorId("editor-1")
                .editorName("editor-1")
                .createdAt(LocalDateTime.now())
                .build();
        return new DocumentOperationService.ApplyResult(duplicated, document, operation, null);
    }
}

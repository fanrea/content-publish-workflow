package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.application.gc.CompactionPolicyEvaluator;
import com.contentworkflow.document.application.gc.DocumentCompactionTask;
import com.contentworkflow.document.application.gc.DocumentCompactionTaskPublisher;
import com.contentworkflow.document.application.gc.TombstoneGcScheduler;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRealtimePushServiceTest {

    private DocumentRealtimeSessionRegistry sessionRegistry;
    private DocumentRealtimeRecentUpdateCache recentUpdateCache;
    private DocumentRealtimeCrossGatewayBroadcaster crossGatewayBroadcaster;
    private CompactionPolicyEvaluator compactionPolicyEvaluator;
    private DocumentCompactionTaskPublisher compactionTaskPublisher;
    private TombstoneGcScheduler tombstoneGcScheduler;
    private ObjectMapper objectMapper;
    private DocumentRealtimePushService pushService;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(DocumentRealtimeSessionRegistry.class);
        recentUpdateCache = mock(DocumentRealtimeRecentUpdateCache.class);
        crossGatewayBroadcaster = mock(DocumentRealtimeCrossGatewayBroadcaster.class);
        compactionPolicyEvaluator = mock(CompactionPolicyEvaluator.class);
        compactionTaskPublisher = mock(DocumentCompactionTaskPublisher.class);
        tombstoneGcScheduler = mock(TombstoneGcScheduler.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(compactionPolicyEvaluator.onOperationApplied(any(DocumentOperation.class))).thenReturn(Optional.empty());
        pushService = new DocumentRealtimePushService(
                sessionRegistry,
                recentUpdateCache,
                crossGatewayBroadcaster,
                compactionPolicyEvaluator,
                compactionTaskPublisher,
                objectMapper
        );
        pushService.setTombstoneGcScheduler(tombstoneGcScheduler);
    }

    @Test
    void broadcastOperationApplied_shouldBroadcastLocalAndPublishCrossGateway() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);

        DocumentOperation operation = operation(100L, 7, 6);

        pushService.broadcastOperationApplied(operation);

        verify(recentUpdateCache, times(1)).append(operation);
        verify(compactionPolicyEvaluator, times(1)).onOperationApplied(operation);
        verify(compactionTaskPublisher, never()).publish(any(DocumentCompactionTask.class));
        verify(tombstoneGcScheduler, times(1)).schedule(100L, 7L);
        verify(crossGatewayBroadcaster, times(1)).publish(any(DocumentWsEvent.class));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(messageCaptor.capture());
        DocumentWsEvent event = objectMapper.readValue(messageCaptor.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(event.type()).isEqualTo("OP_APPLIED");
        assertThat(event.docId()).isEqualTo(100L);
        assertThat(event.revision()).isEqualTo(7);
    }

    @Test
    void broadcastOperationApplied_shouldKeepLocalBroadcastWhenCrossGatewayPublishFails() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);
        doThrow(new RuntimeException("redis unavailable")).when(crossGatewayBroadcaster).publish(any(DocumentWsEvent.class));

        pushService.broadcastOperationApplied(operation(100L, 7, 6));

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastOperationApplied_shouldPublishCompactionTaskWhenEvaluatorTriggers() {
        DocumentCompactionTask task = new DocumentCompactionTask(100L, "UPDATE_COUNT", java.time.Instant.now());
        when(compactionPolicyEvaluator.onOperationApplied(any(DocumentOperation.class))).thenReturn(Optional.of(task));

        pushService.broadcastOperationApplied(operation(100L, 7, 6));

        verify(compactionTaskPublisher, times(1)).publish(task);
    }

    @Test
    void broadcastOperationApplied_shouldUseOperationIdWhenRevisionMissingForGcSchedule() {
        DocumentOperation operation = operation(100L, 7, 6);
        operation.setRevisionNo(null);

        pushService.broadcastOperationApplied(operation);

        verify(tombstoneGcScheduler, times(1)).schedule(100L, 7L);
    }

    @Test
    void publishPresence_shouldOnlyPublishCrossGateway() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);

        pushService.publishPresence(100L, List.of("alice", "bob"), "participant joined");

        verify(crossGatewayBroadcaster, times(1)).publish(any(DocumentWsEvent.class));
        verify(session, never()).sendMessage(any(TextMessage.class));
        verify(recentUpdateCache, never()).append(any(DocumentOperation.class));
    }

    @Test
    void broadcastFromRemote_shouldDeliverLocalOnlyWithoutRepublishOrCacheAppend() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);

        DocumentWsEvent remoteEvent = DocumentWsEvent.applied(
                100L,
                9L,
                9,
                8,
                "u-2",
                "bob",
                null
        );
        pushService.broadcastFromRemote(100L, remoteEvent);

        verify(session, times(1)).sendMessage(any(TextMessage.class));
        verify(crossGatewayBroadcaster, never()).publish(any(DocumentWsEvent.class));
        verify(recentUpdateCache, never()).append(any(DocumentOperation.class));
    }

    private DocumentOperation operation(Long docId, int revision, int baseRevision) {
        return DocumentOperation.builder()
                .id((long) revision)
                .documentId(docId)
                .revisionNo(revision)
                .baseRevision(baseRevision)
                .editorId("u-1")
                .editorName("alice")
                .opType(DocumentOpType.INSERT)
                .opPosition(0)
                .opLength(0)
                .opText("x")
                .createdAt(LocalDateTime.now())
                .build();
    }
}

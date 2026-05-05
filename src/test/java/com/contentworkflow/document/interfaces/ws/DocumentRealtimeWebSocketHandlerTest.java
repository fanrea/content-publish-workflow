package com.contentworkflow.document.interfaces.ws;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.contentworkflow.document.application.ingress.DocumentOperationIngressPublisher;
import com.contentworkflow.document.application.gc.CompactionPolicyEvaluator;
import com.contentworkflow.document.application.gc.NoopDocumentCompactionTaskPublisher;
import com.contentworkflow.document.application.DocumentCollaborationService;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.realtime.DefaultDocumentRealtimeGatewayFacade;
import com.contentworkflow.document.application.realtime.DocumentRealtimeCrossGatewayBroadcaster;
import com.contentworkflow.document.application.realtime.DocumentRealtimeGatewayFacade;
import com.contentworkflow.document.application.realtime.DocumentRealtimePresenceService;
import com.contentworkflow.document.application.realtime.DocumentRealtimePushService;
import com.contentworkflow.document.application.realtime.DocumentRealtimeRecentUpdateCache;
import com.contentworkflow.document.application.realtime.DocumentRealtimeRedisIndex;
import com.contentworkflow.document.application.realtime.DocumentRealtimeSessionRegistry;
import com.contentworkflow.document.application.realtime.NoopDocumentRealtimeCrossGatewayBroadcaster;
import com.contentworkflow.document.application.realtime.NoopDocumentRealtimeRecentUpdateCache;
import com.contentworkflow.document.application.realtime.NoopDocumentRealtimeRedisIndex;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRealtimeWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final DocumentOperationIngressPublisher ingressPublisher = mock(DocumentOperationIngressPublisher.class);
    private final DocumentRealtimeCrossGatewayBroadcaster crossGatewayBroadcaster = mock(DocumentRealtimeCrossGatewayBroadcaster.class);
    private final DocumentRealtimeGatewayFacade gatewayFacade = mock(DocumentRealtimeGatewayFacade.class);
    private final DocumentRealtimePresenceService presenceService = mock(DocumentRealtimePresenceService.class);
    private final DocumentRealtimeSessionRegistry sessionRegistry = mock(DocumentRealtimeSessionRegistry.class);

    private DocumentRealtimeWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        handler = new DocumentRealtimeWebSocketHandler(
                objectMapper,
                ingressPublisher,
                crossGatewayBroadcaster,
                gatewayFacade,
                presenceService,
                sessionRegistry
        );

        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-1");
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);
        when(presenceService.listParticipants(100L)).thenReturn(List.of());
        when(presenceService.join(100L, "ws-1", "alice")).thenReturn(List.of("alice"));
    }

    @Test
    void handleEdit_shouldPublishIngressAndNotCallApplyOperation() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":11,
                  "clientSessionId":"client-s-1",
                  "editorId":"u-1",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":5,
                    "length":0,
                    "text":"hi"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<DocumentOperationIngressCommand> ingressCaptor = ArgumentCaptor.forClass(DocumentOperationIngressCommand.class);
        verify(ingressPublisher).publish(ingressCaptor.capture());
        DocumentOperationIngressCommand command = ingressCaptor.getValue();
        assertThat(command.docId()).isEqualTo(100L);
        assertThat(command.baseRevision()).isEqualTo(3);
        assertThat(command.sessionId()).isEqualTo("client-s-1");
        assertThat(command.clientSeq()).isEqualTo(11L);
        assertThat(command.editorId()).isEqualTo("u-1");
        assertThat(command.editorName()).isEqualTo("alice");
        assertThat(command.op().getOpType().name()).isEqualTo("INSERT");
        assertThat(command.timestamp()).isNotNull();
        assertThat(command.deltaBatchId()).isNull();
        assertThat(command.clientClock()).isNull();
        assertThat(command.baseVector()).isNull();

        verify(gatewayFacade, times(1)).authorizeCanEdit(100L, "u-1");

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(outbound.capture());
        DocumentWsEvent ack = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(ack.type()).isEqualTo("ACK");
        assertThat(ack.message()).isEqualTo("accepted_by_ingress");
        assertThat(ack.revision()).isNull();
        assertThat(ack.clientSeq()).isEqualTo(11L);
        verify(presenceService, never()).upsertSessionClock(eq(100L), eq("ws-1"), any());
    }

    @Test
    void handleEdit_shouldFallbackToWebSocketSessionIdAndServerTimestamp() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":12,
                  "editorId":"u-2",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":0,
                    "text":"x"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<DocumentOperationIngressCommand> ingressCaptor = ArgumentCaptor.forClass(DocumentOperationIngressCommand.class);
        verify(ingressPublisher).publish(ingressCaptor.capture());

        DocumentOperationIngressCommand command = ingressCaptor.getValue();
        assertThat(command.sessionId()).isEqualTo("ws-1");
        assertThat(command.timestamp()).isNotNull();
        assertThat(command.timestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void handleEdit_shouldOnlyAckAcceptedByIngressWithoutImmediateAppliedBroadcast() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":77,
                  "editorId":"u-ack-only",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":1,
                    "text":"q"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(ingressPublisher, times(1)).publish(any(DocumentOperationIngressCommand.class));
        verify(sessionRegistry, never()).sessionsOf(100L);

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(outbound.capture());
        DocumentWsEvent ack = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(ack.type()).isEqualTo("ACK");
        assertThat(ack.message()).isEqualTo("accepted_by_ingress");
        assertThat(ack.revision()).isNull();
    }

    @Test
    void handleEdit_shouldPreferDeltaBatchForIdempotencyAndPassMetadata() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":15,
                  "clientSessionId":"client-s-legacy",
                  "deltaBatchId":"db-100-15",
                  "clientClock":88,
                  "baseVector":{"u-1":87,"u-2":42},
                  "editorId":"u-1",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":1,
                    "text":"x"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<DocumentOperationIngressCommand> ingressCaptor = ArgumentCaptor.forClass(DocumentOperationIngressCommand.class);
        verify(ingressPublisher).publish(ingressCaptor.capture());

        DocumentOperationIngressCommand command = ingressCaptor.getValue();
        assertThat(command.sessionId()).isEqualTo("delta-batch:db-100-15");
        assertThat(command.deltaBatchId()).isEqualTo("db-100-15");
        assertThat(command.clientClock()).isEqualTo(88L);
        assertThat(command.baseVector()).isEqualTo(Map.of("u-1", 87L, "u-2", 42L));
        verify(presenceService, times(1)).upsertSessionClock(100L, "ws-1", 88L);
    }

    @Test
    void handleEdit_shouldNotUpsertSessionClockWhenClientClockMissing() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":17,
                  "editorId":"u-1",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":1,
                    "text":"x"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(ingressPublisher, times(1)).publish(any(DocumentOperationIngressCommand.class));
        verify(presenceService, never()).upsertSessionClock(eq(100L), eq("ws-1"), any());
    }

    @Test
    void handleEdit_shouldRejectInvalidClientClockForLegacyPayloadShape() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":16,
                  "clientClock":0,
                  "editorId":"u-1",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":1,
                    "text":"x"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(ingressPublisher, never()).publish(any(DocumentOperationIngressCommand.class));
        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(outbound.capture());
        DocumentWsEvent error = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(error.type()).isEqualTo("ERROR");
        assertThat(error.message()).isEqualTo("clientClock must be > 0");
    }

    @Test
    void handleEdit_shouldPreferDeltaBatchIdAsIdempotencySessionKey() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":14,
                  "clientSessionId":"client-s-legacy",
                  "deltaBatchId":"batch-001",
                  "clientClock":12345,
                  "baseVector":{"actor-a":12345},
                  "editorId":"u-2",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":0,
                    "text":"x"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<DocumentOperationIngressCommand> ingressCaptor = ArgumentCaptor.forClass(DocumentOperationIngressCommand.class);
        verify(ingressPublisher).publish(ingressCaptor.capture());
        DocumentOperationIngressCommand command = ingressCaptor.getValue();
        assertThat(command.sessionId()).isEqualTo("delta-batch:batch-001");
    }

    @Test
    void handleEdit_shouldRejectInvalidClientClock() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":15,
                  "clientClock":0,
                  "editorId":"u-2",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":0,
                    "text":"x"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(ingressPublisher, never()).publish(any(DocumentOperationIngressCommand.class));
        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(outbound.capture());
        DocumentWsEvent error = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(error.type()).isEqualTo("ERROR");
        assertThat(error.message()).contains("clientClock must be > 0");
    }

    @Test
    void handleEdit_shouldRejectInvalidBaseVector() throws Exception {
        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":16,
                  "baseVector":{"actor-a":-1},
                  "editorId":"u-2",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":0,
                    "text":"x"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(ingressPublisher, never()).publish(any(DocumentOperationIngressCommand.class));
        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(outbound.capture());
        DocumentWsEvent error = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(error.type()).isEqualTo("ERROR");
        assertThat(error.message()).contains("baseVector clock must be >= 0");
    }

    @Test
    void handleEdit_shouldReturnErrorWhenIngressPublishFails() throws Exception {
        doThrow(new IllegalStateException("document operation ingress mq is not enabled"))
                .when(ingressPublisher)
                .publish(any(DocumentOperationIngressCommand.class));

        String payload = """
                {
                  "type":"EDIT_OP",
                  "docId":100,
                  "baseRevision":3,
                  "clientSeq":13,
                  "editorId":"u-3",
                  "editorName":"alice",
                  "op":{
                    "opType":"INSERT",
                    "position":2,
                    "text":"x"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(outbound.capture());
        DocumentWsEvent error = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(error.type()).isEqualTo("ERROR");
        assertThat(error.message()).isEqualTo("failed to publish edit operation ingress command");
        assertThat(error.docId()).isEqualTo(100L);
        assertThat(error.clientSeq()).isEqualTo(13L);
    }

    @Test
    void handleSync_shouldReplayOpsFromFacade() throws Exception {
        DocumentOperation cachedOp = DocumentOperation.builder()
                .id(1001L)
                .documentId(100L)
                .revisionNo(4)
                .baseRevision(3)
                .editorId("u-1")
                .editorName("alice")
                .opType(DocumentOpType.INSERT)
                .opPosition(1)
                .opLength(0)
                .opText("x")
                .createdAt(LocalDateTime.now())
                .build();
        when(gatewayFacade.prepareSync(100L, "u-1", 3, 200)).thenReturn(
                DocumentRealtimeGatewayFacade.SyncDecision.replay(List.of(cachedOp), 5)
        );

        String payload = """
                {
                  "type":"SYNC_OPS",
                  "docId":100,
                  "baseRevision":3,
                  "editorId":"u-1",
                  "editorName":"alice"
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(outbound.capture());
        verify(sessionRegistry, times(1)).bind(100L, session);
        verify(presenceService, times(1)).join(100L, "ws-1", "alice");

        DocumentWsEvent applied = objectMapper.readValue(outbound.getAllValues().get(0).getPayload(), DocumentWsEvent.class);
        DocumentWsEvent syncDone = objectMapper.readValue(outbound.getAllValues().get(1).getPayload(), DocumentWsEvent.class);
        assertThat(applied.type()).isEqualTo("OP_APPLIED");
        assertThat(applied.revision()).isEqualTo(4);
        assertThat(syncDone.type()).isEqualTo("SYNC_DONE");
        assertThat(syncDone.latestRevision()).isEqualTo(5);
    }

    @Test
    void handleSync_shouldReturnInstructionWhenFacadeRequiresPull() throws Exception {
        when(gatewayFacade.prepareSync(100L, "u-1", 3, 200)).thenReturn(
                DocumentRealtimeGatewayFacade.SyncDecision.instruction(
                        3,
                        "PULL_UPDATES_REQUIRED",
                        "strict_stateless_sync: pull updates via HTTP"
                )
        );

        String payload = """
                {
                  "type":"SYNC_OPS",
                  "docId":100,
                  "baseRevision":3,
                  "editorId":"u-1",
                  "editorName":"alice"
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(outbound.capture());
        verify(sessionRegistry, times(1)).bind(100L, session);
        verify(presenceService, times(1)).join(100L, "ws-1", "alice");
        DocumentWsEvent event = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(event.type()).isEqualTo("PULL_UPDATES_REQUIRED");
        assertThat(event.message()).contains("strict_stateless_sync");
        assertThat(event.baseRevision()).isEqualTo(3);
    }

    @Test
    void handleSync_shouldRejectInvalidBaseRevisionWithoutBindingSession() throws Exception {
        String payload = """
                {
                  "type":"SYNC_OPS",
                  "docId":100,
                  "baseRevision":-1,
                  "editorId":"u-1",
                  "editorName":"alice"
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(sessionRegistry, never()).bind(100L, session);
        verify(presenceService, never()).join(100L, "ws-1", "alice");
        verify(gatewayFacade, never()).prepareSync(anyLong(), anyString(), anyInt(), anyInt());

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(outbound.capture());
        DocumentWsEvent event = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(event.type()).isEqualTo("ERROR");
        assertThat(event.message()).contains("baseRevision must be >= 0");
    }

    @Test
    void handleSync_shouldBindSessionSoSubsequentBroadcastCanBeReceived() throws Exception {
        DocumentOperationIngressPublisher localIngressPublisher = mock(DocumentOperationIngressPublisher.class);
        DocumentRealtimeGatewayFacade localGatewayFacade = mock(DocumentRealtimeGatewayFacade.class);
        DocumentRealtimeRedisIndex redisIndex = new NoopDocumentRealtimeRedisIndex();
        DocumentRealtimeSessionRegistry localSessionRegistry = new DocumentRealtimeSessionRegistry(redisIndex);
        DocumentRealtimePresenceService localPresenceService = new DocumentRealtimePresenceService(redisIndex);
        DocumentRealtimeWebSocketHandler localHandler = new DocumentRealtimeWebSocketHandler(
                objectMapper,
                localIngressPublisher,
                new NoopDocumentRealtimeCrossGatewayBroadcaster(),
                localGatewayFacade,
                localPresenceService,
                localSessionRegistry
        );
        DocumentRealtimeRecentUpdateCache recentUpdateCache = new NoopDocumentRealtimeRecentUpdateCache();
        DocumentRealtimePushService pushService = new DocumentRealtimePushService(
                localSessionRegistry,
                recentUpdateCache,
                new NoopDocumentRealtimeCrossGatewayBroadcaster(),
                new CompactionPolicyEvaluator(200, 1.5d, Duration.ofMinutes(10), Duration.ofSeconds(30)),
                new NoopDocumentCompactionTaskPublisher(),
                objectMapper
        );

        WebSocketSession localSession = mock(WebSocketSession.class);
        when(localSession.getId()).thenReturn("ws-sync-1");
        when(localSession.isOpen()).thenReturn(true);
        when(localSession.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(localGatewayFacade.prepareSync(100L, "u-1", 3, 200)).thenReturn(
                DocumentRealtimeGatewayFacade.SyncDecision.replay(List.of(), 3)
        );

        String syncPayload = """
                {
                  "type":"SYNC_OPS",
                  "docId":100,
                  "baseRevision":3,
                  "editorId":"u-1",
                  "editorName":"alice"
                }
                """;
        localHandler.handleTextMessage(localSession, new TextMessage(syncPayload));

        DocumentOperation remoteOp = DocumentOperation.builder()
                .id(2001L)
                .documentId(100L)
                .revisionNo(4)
                .baseRevision(3)
                .editorId("u-2")
                .editorName("bob")
                .opType(DocumentOpType.INSERT)
                .opPosition(0)
                .opLength(0)
                .opText("z")
                .createdAt(LocalDateTime.now())
                .build();
        pushService.broadcastOperationApplied(remoteOp);

        assertThat(localPresenceService.listParticipants(100L)).containsExactly("alice");

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(localSession, times(2)).sendMessage(outbound.capture());
        DocumentWsEvent syncDone = objectMapper.readValue(outbound.getAllValues().get(0).getPayload(), DocumentWsEvent.class);
        DocumentWsEvent applied = objectMapper.readValue(outbound.getAllValues().get(1).getPayload(), DocumentWsEvent.class);
        assertThat(syncDone.type()).isEqualTo("SYNC_DONE");
        assertThat(applied.type()).isEqualTo("OP_APPLIED");
        assertThat(applied.docId()).isEqualTo(100L);
        assertThat(applied.revision()).isEqualTo(4);
    }

    @Test
    void handleSync_shouldTriggerPullInstructionWhenSubsequentAppliedRevisionHasGap() throws Exception {
        DocumentOperationIngressPublisher localIngressPublisher = mock(DocumentOperationIngressPublisher.class);
        DocumentRealtimeGatewayFacade localGatewayFacade = mock(DocumentRealtimeGatewayFacade.class);
        DocumentRealtimeRedisIndex redisIndex = new NoopDocumentRealtimeRedisIndex();
        DocumentRealtimeSessionRegistry localSessionRegistry = new DocumentRealtimeSessionRegistry(redisIndex);
        DocumentRealtimePresenceService localPresenceService = new DocumentRealtimePresenceService(redisIndex);
        DocumentRealtimeWebSocketHandler localHandler = new DocumentRealtimeWebSocketHandler(
                objectMapper,
                localIngressPublisher,
                new NoopDocumentRealtimeCrossGatewayBroadcaster(),
                localGatewayFacade,
                localPresenceService,
                localSessionRegistry
        );
        DocumentRealtimeRecentUpdateCache recentUpdateCache = new NoopDocumentRealtimeRecentUpdateCache();
        DocumentRealtimePushService pushService = new DocumentRealtimePushService(
                localSessionRegistry,
                recentUpdateCache,
                new NoopDocumentRealtimeCrossGatewayBroadcaster(),
                new CompactionPolicyEvaluator(200, 1.5d, Duration.ofMinutes(10), Duration.ofSeconds(30)),
                new NoopDocumentCompactionTaskPublisher(),
                objectMapper
        );

        WebSocketSession localSession = mock(WebSocketSession.class);
        when(localSession.getId()).thenReturn("ws-sync-gap-1");
        when(localSession.isOpen()).thenReturn(true);
        when(localSession.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(localGatewayFacade.prepareSync(100L, "u-1", 5, 200)).thenReturn(
                DocumentRealtimeGatewayFacade.SyncDecision.replay(List.of(), 5)
        );

        String syncPayload = """
                {
                  "type":"SYNC_OPS",
                  "docId":100,
                  "baseRevision":5,
                  "editorId":"u-1",
                  "editorName":"alice"
                }
                """;
        localHandler.handleTextMessage(localSession, new TextMessage(syncPayload));

        DocumentOperation remoteOp = DocumentOperation.builder()
                .id(2007L)
                .documentId(100L)
                .revisionNo(7)
                .baseRevision(6)
                .editorId("u-2")
                .editorName("bob")
                .opType(DocumentOpType.INSERT)
                .opPosition(0)
                .opLength(0)
                .opText("z")
                .createdAt(LocalDateTime.now())
                .build();
        pushService.broadcastOperationApplied(remoteOp);

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(localSession, times(2)).sendMessage(outbound.capture());
        DocumentWsEvent syncDone = objectMapper.readValue(outbound.getAllValues().get(0).getPayload(), DocumentWsEvent.class);
        DocumentWsEvent instruction = objectMapper.readValue(outbound.getAllValues().get(1).getPayload(), DocumentWsEvent.class);
        assertThat(syncDone.type()).isEqualTo("SYNC_DONE");
        assertThat(instruction.type()).isEqualTo("PULL_UPDATES_REQUIRED");
        assertThat(instruction.baseRevision()).isEqualTo(5);
        assertThat(instruction.latestRevision()).isEqualTo(7);
    }

    @Test
    void handleJoin_shouldReturnInstructionWhenFacadeCannotProvideSnapshot() throws Exception {
        when(gatewayFacade.prepareJoin(100L, "u-1")).thenReturn(
                DocumentRealtimeGatewayFacade.JoinDecision.instruction(
                        "PULL_SNAPSHOT_REQUIRED",
                        "strict_stateless_join: pull snapshot via HTTP"
                )
        );
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));

        String payload = """
                {
                  "type":"JOIN",
                  "docId":100,
                  "editorId":"u-1",
                  "editorName":"alice"
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(outbound.capture());
        DocumentWsEvent instruction = objectMapper.readValue(outbound.getAllValues().get(0).getPayload(), DocumentWsEvent.class);
        assertThat(instruction.type()).isEqualTo("PULL_SNAPSHOT_REQUIRED");
        assertThat(instruction.message()).contains("strict_stateless_join");

        verify(gatewayFacade, never()).authorizeMember(anyLong(), anyString());
        verify(gatewayFacade, never()).authorizeCanEdit(anyLong(), anyString());
        verify(gatewayFacade, never()).prepareSync(anyLong(), anyString(), anyInt(), anyInt());
    }

    @Test
    void handleCursorMove_shouldBroadcastLocallyAndPublishCrossGateway() throws Exception {
        WebSocketSession peer = mock(WebSocketSession.class);
        when(peer.getId()).thenReturn("ws-2");
        when(peer.isOpen()).thenReturn(true);
        when(sessionRegistry.resolveSendSession(peer)).thenReturn(peer);
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session, peer));

        String payload = """
                {
                  "type":"CURSOR_MOVE",
                  "docId":100,
                  "editorId":"u-1",
                  "editorName":"alice",
                  "cursor":{"start":1,"end":3}
                }
                """;

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(ingressPublisher, never()).publish(any(DocumentOperationIngressCommand.class));
        verify(session, never()).sendMessage(any(TextMessage.class));
        verify(peer, times(1)).sendMessage(any(TextMessage.class));
        verify(crossGatewayBroadcaster, never()).publish(any(DocumentWsEvent.class));
        verify(crossGatewayBroadcaster, times(1)).publishTransient(any(DocumentWsEvent.class));
    }

    @Test
    void strictStatelessFacade_shouldNotInvokeSynchronousDataOrPermissionLookups() {
        DocumentCollaborationService documentService = mock(DocumentCollaborationService.class);
        DocumentPermissionService permissionService = mock(DocumentPermissionService.class);
        com.contentworkflow.document.application.realtime.DocumentOperationService operationService =
                mock(com.contentworkflow.document.application.realtime.DocumentOperationService.class);
        DocumentRealtimeRecentUpdateCache recentUpdateCache = mock(DocumentRealtimeRecentUpdateCache.class);
        when(recentUpdateCache.replaySince(100L, 7, 200))
                .thenReturn(new DocumentRealtimeRecentUpdateCache.ReplayResult(List.of(), true));

        DefaultDocumentRealtimeGatewayFacade facade = new DefaultDocumentRealtimeGatewayFacade(
                documentService,
                permissionService,
                operationService,
                recentUpdateCache,
                true
        );

        facade.prepareJoin(100L, "u-1");
        facade.authorizeMember(100L, "u-1");
        facade.authorizeCanEdit(100L, "u-1");
        facade.prepareSync(100L, "u-1", 7, 200);
        facade.resolveConflict(100L);

        verify(documentService, never()).getDocument(anyLong());
        verify(permissionService, never()).requireMember(anyLong(), anyString());
        verify(permissionService, never()).requireCanEdit(anyLong(), anyString());
        verify(operationService, never()).listOperationsSince(anyLong(), anyInt(), anyInt());
    }
}

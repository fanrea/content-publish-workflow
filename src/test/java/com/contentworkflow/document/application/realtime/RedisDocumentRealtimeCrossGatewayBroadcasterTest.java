package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressPublisher;
import com.contentworkflow.document.interfaces.ws.DocumentRealtimeWebSocketHandler;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsEvent;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDocumentRealtimeCrossGatewayBroadcasterTest {

    private StringRedisTemplate redisTemplate;
    private DocumentRealtimeSessionRegistry sessionRegistry;
    private ObjectMapper objectMapper;
    private RedisDocumentRealtimeCrossGatewayBroadcaster broadcaster;
    private SetOperations<String, String> setOperations;
    private HashOperations<String, Object, Object> hashOperations;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        sessionRegistry = mock(DocumentRealtimeSessionRegistry.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();

        hashOperations = (HashOperations<String, Object, Object>) mock(HashOperations.class);
        setOperations = (SetOperations<String, String>) mock(SetOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(anyString())).thenReturn(Set.of());
        broadcaster = new RedisDocumentRealtimeCrossGatewayBroadcaster(
                redisTemplate,
                sessionRegistry,
                objectMapper,
                "gw-1",
                "cpw:realtime:broadcast"
        );
    }

    @Test
    void onBroadcastPayload_shouldForwardRemoteOperationAppliedToLocalSessions() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);

        DocumentWsOperation operation = new DocumentWsOperation();
        operation.setOpType(DocumentOpType.INSERT);
        operation.setPosition(0);
        operation.setLength(0);
        operation.setText("x");
        DocumentWsEvent event = DocumentWsEvent.applied(100L, 7L, 7, 6, "u-1", "alice", operation);
        String payload = objectMapper.writeValueAsString(
                new DocumentRealtimeCrossGatewayEnvelope("gw-2", 100L, event)
        );

        broadcaster.onBroadcastPayload(payload);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(messageCaptor.capture());
        DocumentWsEvent pushed = objectMapper.readValue(messageCaptor.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(pushed.type()).isEqualTo("OP_APPLIED");
        assertThat(pushed.docId()).isEqualTo(100L);
        assertThat(pushed.revision()).isEqualTo(7);
    }

    @SuppressWarnings("unchecked")
    @Test
    void onBroadcastPayload_shouldRefreshPresenceParticipantsFromRedisOnlineUsers() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);

        when(hashOperations.entries("doc:100:online_users")).thenReturn(Map.of("bob", "1", "alice", "2"));

        DocumentWsEvent event = DocumentWsEvent.presence(100L, List.of("alice"), "participant joined");
        String payload = objectMapper.writeValueAsString(
                new DocumentRealtimeCrossGatewayEnvelope("gw-2", 100L, event)
        );

        broadcaster.onBroadcastPayload(payload);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(messageCaptor.capture());
        DocumentWsEvent pushed = objectMapper.readValue(messageCaptor.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(pushed.type()).isEqualTo("PRESENCE");
        assertThat(pushed.participants()).containsExactly("alice", "bob");
    }

    @Test
    void publish_shouldRouteOnlyToRoomGatewaysAndSkipSourceGateway() {
        DocumentWsEvent event = DocumentWsEvent.presence(100L, List.of("alice"), "participant joined");
        when(setOperations.members("doc:100:room_gateways")).thenReturn(Set.of("gw-2", "gw-3", "gw-1", " "));

        broadcaster.publish(event);

        verify(redisTemplate, times(1)).convertAndSend("cpw:realtime:broadcast:gw-2", org.mockito.ArgumentMatchers.anyString());
        verify(redisTemplate, times(1)).convertAndSend("cpw:realtime:broadcast:gw-3", org.mockito.ArgumentMatchers.anyString());
        verify(redisTemplate, never()).convertAndSend("cpw:realtime:broadcast:gw-1", org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void publish_shouldSkipWhenRoomRouteMissing() {
        DocumentWsEvent event = DocumentWsEvent.presence(100L, List.of("alice"), "participant joined");
        when(setOperations.members("doc:100:room_gateways")).thenReturn(Set.of());

        broadcaster.publish(event);

        verify(redisTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void onBroadcastPayload_shouldSkipEventsFromSameGateway() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));

        DocumentWsEvent event = DocumentWsEvent.presence(100L, List.of("alice"), "participant joined");
        String payload = objectMapper.writeValueAsString(
                new DocumentRealtimeCrossGatewayEnvelope("gw-1", 100L, event)
        );

        broadcaster.onBroadcastPayload(payload);

        verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void onBroadcastPayload_shouldReachSessionRejoinedBySyncOps() throws Exception {
        StringRedisTemplate localRedisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> localHashOps =
                (HashOperations<String, Object, Object>) mock(HashOperations.class);
        when(localRedisTemplate.opsForHash()).thenReturn(localHashOps);

        DocumentRealtimeRedisIndex redisIndex = new NoopDocumentRealtimeRedisIndex();
        DocumentRealtimeSessionRegistry localRegistry = new DocumentRealtimeSessionRegistry(redisIndex);
        DocumentRealtimePresenceService localPresenceService = new DocumentRealtimePresenceService(redisIndex);
        DocumentRealtimeGatewayFacade localGatewayFacade = mock(DocumentRealtimeGatewayFacade.class);
        when(localGatewayFacade.prepareSync(100L, "u-1", 3, 200)).thenReturn(
                DocumentRealtimeGatewayFacade.SyncDecision.replay(List.of(), 3)
        );
        DocumentRealtimeWebSocketHandler handler = new DocumentRealtimeWebSocketHandler(
                objectMapper,
                mock(DocumentOperationIngressPublisher.class),
                new NoopDocumentRealtimeCrossGatewayBroadcaster(),
                localGatewayFacade,
                localPresenceService,
                localRegistry
        );
        RedisDocumentRealtimeCrossGatewayBroadcaster localBroadcaster =
                new RedisDocumentRealtimeCrossGatewayBroadcaster(
                        localRedisTemplate,
                        localRegistry,
                        objectMapper,
                        "gw-local",
                        "cpw:realtime:broadcast"
                );

        WebSocketSession localSession = mock(WebSocketSession.class);
        when(localSession.getId()).thenReturn("ws-sync-1");
        when(localSession.isOpen()).thenReturn(true);
        when(localSession.getAttributes()).thenReturn(new ConcurrentHashMap<>());

        handler.handleTextMessage(localSession, new TextMessage("""
                {
                  "type":"SYNC_OPS",
                  "docId":100,
                  "baseRevision":3,
                  "editorId":"u-1",
                  "editorName":"alice"
                }
                """));

        DocumentWsOperation op = new DocumentWsOperation();
        op.setOpType(DocumentOpType.INSERT);
        op.setPosition(0);
        op.setLength(0);
        op.setText("x");
        String payload = objectMapper.writeValueAsString(
                new DocumentRealtimeCrossGatewayEnvelope(
                        "gw-remote",
                        100L,
                        DocumentWsEvent.applied(100L, 11L, 11, 10, "u-2", "bob", op)
                )
        );
        localBroadcaster.onBroadcastPayload(payload);

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(localSession, times(2)).sendMessage(outbound.capture());
        DocumentWsEvent syncDone = objectMapper.readValue(outbound.getAllValues().get(0).getPayload(), DocumentWsEvent.class);
        DocumentWsEvent applied = objectMapper.readValue(outbound.getAllValues().get(1).getPayload(), DocumentWsEvent.class);
        assertThat(syncDone.type()).isEqualTo("SYNC_DONE");
        assertThat(applied.type()).isEqualTo("OP_APPLIED");
        assertThat(applied.revision()).isEqualTo(11);
        assertThat(localPresenceService.listParticipants(100L)).containsExactly("alice");
    }

    @SuppressWarnings("unchecked")
    @Test
    void onBroadcastPayload_shouldReachSessionJoinedByJoinMessage() throws Exception {
        StringRedisTemplate localRedisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> localHashOps =
                (HashOperations<String, Object, Object>) mock(HashOperations.class);
        when(localRedisTemplate.opsForHash()).thenReturn(localHashOps);
        when(localHashOps.entries("doc:100:online_users")).thenReturn(Map.of("alice", "1", "bob", "1"));

        DocumentRealtimeRedisIndex redisIndex = new NoopDocumentRealtimeRedisIndex();
        DocumentRealtimeSessionRegistry localRegistry = new DocumentRealtimeSessionRegistry(redisIndex);
        DocumentRealtimePresenceService localPresenceService = new DocumentRealtimePresenceService(redisIndex);
        DocumentRealtimeGatewayFacade localGatewayFacade = mock(DocumentRealtimeGatewayFacade.class);
        when(localGatewayFacade.prepareJoin(100L, "u-1")).thenReturn(
                DocumentRealtimeGatewayFacade.JoinDecision.instruction(
                        "PULL_SNAPSHOT_REQUIRED",
                        "strict_stateless_join: pull snapshot via HTTP"
                )
        );
        DocumentRealtimeWebSocketHandler handler = new DocumentRealtimeWebSocketHandler(
                objectMapper,
                mock(DocumentOperationIngressPublisher.class),
                new NoopDocumentRealtimeCrossGatewayBroadcaster(),
                localGatewayFacade,
                localPresenceService,
                localRegistry
        );
        RedisDocumentRealtimeCrossGatewayBroadcaster localBroadcaster =
                new RedisDocumentRealtimeCrossGatewayBroadcaster(
                        localRedisTemplate,
                        localRegistry,
                        objectMapper,
                        "gw-local",
                        "cpw:realtime:broadcast"
                );

        WebSocketSession localSession = mock(WebSocketSession.class);
        when(localSession.getId()).thenReturn("ws-join-1");
        when(localSession.isOpen()).thenReturn(true);
        when(localSession.getAttributes()).thenReturn(new ConcurrentHashMap<>());

        handler.handleTextMessage(localSession, new TextMessage("""
                {
                  "type":"JOIN",
                  "docId":100,
                  "editorId":"u-1",
                  "editorName":"alice"
                }
                """));

        String payload = objectMapper.writeValueAsString(
                new DocumentRealtimeCrossGatewayEnvelope(
                        "gw-remote",
                        100L,
                        DocumentWsEvent.presence(100L, List.of("alice"), "participant joined")
                )
        );
        localBroadcaster.onBroadcastPayload(payload);

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(localSession, times(3)).sendMessage(outbound.capture());
        DocumentWsEvent instruction = objectMapper.readValue(outbound.getAllValues().get(0).getPayload(), DocumentWsEvent.class);
        DocumentWsEvent localPresence = objectMapper.readValue(outbound.getAllValues().get(1).getPayload(), DocumentWsEvent.class);
        DocumentWsEvent remotePresence = objectMapper.readValue(outbound.getAllValues().get(2).getPayload(), DocumentWsEvent.class);
        assertThat(instruction.type()).isEqualTo("PULL_SNAPSHOT_REQUIRED");
        assertThat(localPresence.type()).isEqualTo("PRESENCE");
        assertThat(remotePresence.type()).isEqualTo("PRESENCE");
        assertThat(remotePresence.participants()).containsExactly("alice", "bob");
        assertThat(localPresenceService.listParticipants(100L)).containsExactly("alice");
    }

    @Test
    void onBroadcastPayload_shouldDeduplicateSamePayloadWithinWindow() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);
        when(sessionRegistry.sessionsOf(100L)).thenReturn(List.of(session));
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);

        DocumentWsEvent event = DocumentWsEvent.presence(100L, List.of("alice"), "participant joined");
        String payload = objectMapper.writeValueAsString(
                new DocumentRealtimeCrossGatewayEnvelope("gw-2", 100L, event)
        );

        broadcaster.onBroadcastPayload(payload);
        broadcaster.onBroadcastPayload(payload);

        verify(session, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    }
}

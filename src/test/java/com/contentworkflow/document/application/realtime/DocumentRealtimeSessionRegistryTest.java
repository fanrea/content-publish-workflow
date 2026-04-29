package com.contentworkflow.document.application.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentRealtimeSessionRegistryTest {

    private DocumentRealtimeSessionRegistry registry;
    private RecordingRedisIndex redisIndex;

    @BeforeEach
    void setUp() {
        redisIndex = new RecordingRedisIndex();
        registry = new DocumentRealtimeSessionRegistry(redisIndex);
    }

    @Test
    void bind_shouldStoreConcurrentDecoratorForSession() {
        WebSocketSession rawSession = mock(WebSocketSession.class);
        when(rawSession.getId()).thenReturn("s-1");
        when(rawSession.isOpen()).thenReturn(true);

        registry.bind(100L, rawSession);

        Collection<WebSocketSession> sessions = registry.sessionsOf(100L);
        assertThat(sessions).hasSize(1);
        WebSocketSession stored = sessions.iterator().next();
        assertThat(stored).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
        assertThat(stored.getId()).isEqualTo("s-1");
    }

    @Test
    void bind_shouldReuseExistingDecoratorWhenSessionIdAlreadyBound() {
        WebSocketSession firstSession = mock(WebSocketSession.class);
        when(firstSession.getId()).thenReturn("s-1");
        when(firstSession.isOpen()).thenReturn(true);

        WebSocketSession secondSession = mock(WebSocketSession.class);
        when(secondSession.getId()).thenReturn("s-1");

        registry.bind(100L, firstSession);
        WebSocketSession storedAfterFirstBind = registry.sessionsOf(100L).iterator().next();

        registry.bind(100L, secondSession);
        WebSocketSession storedAfterSecondBind = registry.sessionsOf(100L).iterator().next();

        assertThat(storedAfterSecondBind).isSameAs(storedAfterFirstBind);
    }

    @Test
    void bindAndUnbind_shouldMaintainRedisRoomAndGatewaySessionIndex() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);

        registry.bind(100L, session);
        registry.bind(200L, session);

        assertThat(redisIndex.roomGatewayAdds).containsExactlyInAnyOrder(100L, 200L);
        assertThat(redisIndex.gatewaySessionAdds).containsExactly("s-1");

        registry.unbind(100L, "s-1");
        assertThat(redisIndex.roomGatewayRemoves).containsExactly(100L);
        assertThat(redisIndex.gatewaySessionRemoves).isEmpty();

        registry.unbind(200L, "s-1");
        assertThat(redisIndex.roomGatewayRemoves).containsExactlyInAnyOrder(100L, 200L);
        assertThat(redisIndex.gatewaySessionRemoves).containsExactly("s-1");
    }

    @Test
    void removeSession_shouldCleanupRedisIndexesForAllDocuments() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);

        registry.bind(100L, session);
        registry.bind(200L, session);

        registry.removeSession("s-1");

        assertThat(redisIndex.gatewaySessionRemoves).containsExactly("s-1");
        assertThat(redisIndex.roomGatewayRemoves).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void unbind_shouldNotRemoveRoomGatewayWhenDocumentStillHasOtherSessions() {
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("s-1");
        when(session1.isOpen()).thenReturn(true);

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("s-2");
        when(session2.isOpen()).thenReturn(true);

        registry.bind(100L, session1);
        registry.bind(100L, session2);
        registry.unbind(100L, "s-1");

        assertThat(redisIndex.roomGatewayRemoves).isEmpty();
    }

    private static final class RecordingRedisIndex implements DocumentRealtimeRedisIndex {
        private final Set<Long> roomGatewayAdds = new HashSet<>();
        private final Set<Long> roomGatewayRemoves = new HashSet<>();
        private final Set<String> gatewaySessionAdds = new HashSet<>();
        private final Set<String> gatewaySessionRemoves = new HashSet<>();

        @Override
        public void addRoomGateway(Long documentId) {
            roomGatewayAdds.add(documentId);
        }

        @Override
        public void removeRoomGateway(Long documentId) {
            roomGatewayRemoves.add(documentId);
        }

        @Override
        public void addGatewaySession(String sessionId) {
            gatewaySessionAdds.add(sessionId);
        }

        @Override
        public void removeGatewaySession(String sessionId) {
            gatewaySessionRemoves.add(sessionId);
        }

        @Override
        public void incrementOnlineUser(Long documentId, String userId) {
            // no-op for this test
        }

        @Override
        public void decrementOnlineUser(Long documentId, String userId) {
            // no-op for this test
        }
    }
}

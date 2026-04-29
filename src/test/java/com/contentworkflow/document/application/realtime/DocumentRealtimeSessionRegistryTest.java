package com.contentworkflow.document.application.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentRealtimeSessionRegistryTest {

    private DocumentRealtimeSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DocumentRealtimeSessionRegistry();
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
}

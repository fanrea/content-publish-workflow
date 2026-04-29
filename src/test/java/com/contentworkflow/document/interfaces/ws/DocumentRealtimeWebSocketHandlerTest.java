package com.contentworkflow.document.interfaces.ws;

import com.contentworkflow.document.application.DocumentCollaborationService;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.contentworkflow.document.application.ingress.DocumentOperationIngressPublisher;
import com.contentworkflow.document.application.realtime.DocumentOperationService;
import com.contentworkflow.document.application.realtime.DocumentRealtimePresenceService;
import com.contentworkflow.document.application.realtime.DocumentRealtimeSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRealtimeWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final DocumentCollaborationService documentService = mock(DocumentCollaborationService.class);
    private final DocumentPermissionService permissionService = mock(DocumentPermissionService.class);
    private final DocumentOperationIngressPublisher ingressPublisher = mock(DocumentOperationIngressPublisher.class);
    private final DocumentOperationService operationService = mock(DocumentOperationService.class);
    private final DocumentRealtimePresenceService presenceService = mock(DocumentRealtimePresenceService.class);
    private final DocumentRealtimeSessionRegistry sessionRegistry = mock(DocumentRealtimeSessionRegistry.class);

    private DocumentRealtimeWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        handler = new DocumentRealtimeWebSocketHandler(
                objectMapper,
                documentService,
                permissionService,
                ingressPublisher,
                operationService,
                presenceService,
                sessionRegistry
        );

        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-1");
        when(session.isOpen()).thenReturn(true);
        when(sessionRegistry.resolveSendSession(session)).thenReturn(session);
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

        verify(operationService, never()).applyOperation(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(outbound.capture());
        DocumentWsEvent ack = objectMapper.readValue(outbound.getValue().getPayload(), DocumentWsEvent.class);
        assertThat(ack.type()).isEqualTo("ACK");
        assertThat(ack.message()).isEqualTo("accepted_by_ingress");
        assertThat(ack.revision()).isNull();
        assertThat(ack.clientSeq()).isEqualTo(11L);
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
}

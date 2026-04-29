package com.contentworkflow.document.interfaces.ws;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.DocumentCollaborationService;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.contentworkflow.document.application.ingress.DocumentOperationIngressPublisher;
import com.contentworkflow.document.application.realtime.DocumentOperationService;
import com.contentworkflow.document.application.realtime.DocumentRealtimePresenceService;
import com.contentworkflow.document.application.realtime.DocumentRealtimeSessionRegistry;
import com.contentworkflow.document.domain.entity.CollaborativeDocument;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Native WebSocket handler (without STOMP).
 */
@Component
public class DocumentRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentRealtimeWebSocketHandler.class);
    private static final int DEFAULT_SYNC_LIMIT = 200;

    private final ObjectMapper objectMapper;
    private final DocumentCollaborationService documentService;
    private final DocumentPermissionService permissionService;
    private final DocumentOperationIngressPublisher ingressPublisher;
    private final DocumentOperationService operationService;
    private final DocumentRealtimePresenceService presenceService;
    private final DocumentRealtimeSessionRegistry sessionRegistry;

    public DocumentRealtimeWebSocketHandler(ObjectMapper objectMapper,
                                            DocumentCollaborationService documentService,
                                            DocumentPermissionService permissionService,
                                            DocumentOperationIngressPublisher ingressPublisher,
                                            DocumentOperationService operationService,
                                            DocumentRealtimePresenceService presenceService,
                                            DocumentRealtimeSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.documentService = documentService;
        this.permissionService = permissionService;
        this.ingressPublisher = ingressPublisher;
        this.operationService = operationService;
        this.presenceService = presenceService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DocumentWsMessage inbound;
        try {
            inbound = objectMapper.readValue(message.getPayload(), DocumentWsMessage.class);
        } catch (Exception ex) {
            sendSafe(session, DocumentWsEvent.error(null, "invalid websocket payload", null));
            return;
        }

        String type = normalizeType(inbound.getType());
        try {
            switch (type) {
                case "JOIN" -> handleJoin(session, inbound);
                case "LEAVE" -> handleLeave(session, inbound);
                case "EDIT_OP" -> handleEdit(session, inbound);
                case "SYNC_OPS" -> handleSyncOps(session, inbound);
                case "CURSOR_MOVE" -> handleCursorMove(session, inbound);
                default -> sendSafe(session, DocumentWsEvent.error(inbound.getDocId(), "unsupported message type: " + type, inbound.getClientSeq()));
            }
        } catch (BusinessException ex) {
            if ("DOCUMENT_CONCURRENT_MODIFICATION".equals(ex.getCode())) {
                handleConflict(session, inbound);
                return;
            }
            sendSafe(session, DocumentWsEvent.error(inbound.getDocId(), ex.getMessage(), inbound.getClientSeq()));
        } catch (Exception ex) {
            log.error("websocket handle error, sessionId={}, type={}, payload={}", session.getId(), type, message.getPayload(), ex);
            sendSafe(session, DocumentWsEvent.error(inbound.getDocId(), "internal server error", inbound.getClientSeq()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        List<Long> affectedDocs = sessionRegistry.removeSession(session.getId());
        List<Long> affectedPresenceDocs = presenceService.removeSession(session.getId());
        for (Long docId : affectedDocs) {
            broadcast(
                    docId,
                    DocumentWsEvent.presence(docId, presenceService.listParticipants(docId), "participant disconnected"),
                    null
            );
        }
        for (Long docId : affectedPresenceDocs) {
            if (!affectedDocs.contains(docId)) {
                broadcast(
                        docId,
                        DocumentWsEvent.presence(docId, presenceService.listParticipants(docId), "participant disconnected"),
                        null
                );
            }
        }
    }

    private void handleJoin(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = requireDocumentId(inbound.getDocId());
        permissionService.requireMember(docId, normalizeEditorId(inbound.getEditorId()));

        sessionRegistry.bind(docId, session);
        List<String> participants = presenceService.join(docId, session.getId(), inbound.getEditorName());

        CollaborativeDocument document = documentService.getDocument(docId);
        sendSafe(session, DocumentWsEvent.snapshot(
                document.getId(),
                document.getLatestRevision(),
                document.getTitle(),
                document.getContent()
        ));
        broadcast(docId, DocumentWsEvent.presence(docId, participants, "participant joined"), null);
    }

    private void handleLeave(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = requireDocumentId(inbound.getDocId());
        sessionRegistry.unbind(docId, session.getId());
        List<String> participants = presenceService.leave(docId, session.getId());
        broadcast(docId, DocumentWsEvent.presence(docId, participants, "participant left"), null);
    }

    private void handleEdit(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = requireDocumentId(inbound.getDocId());
        Integer baseRevision = requireEditBaseRevision(inbound.getBaseRevision());
        Long clientSeq = requireClientSeq(inbound.getClientSeq());
        String normalizedEditorId = normalizeEditorId(inbound.getEditorId());
        String normalizedEditorName = normalizeEditorName(inbound.getEditorName());
        DocumentWsOperation normalizedOperation = normalizeEditOperation(inbound.getOp());

        permissionService.requireCanEdit(docId, normalizedEditorId);

        sessionRegistry.bind(docId, session);
        presenceService.join(docId, session.getId(), normalizedEditorName);

        DocumentOperationIngressCommand ingressCommand = new DocumentOperationIngressCommand(
                docId,
                baseRevision,
                resolveLogicalSessionId(inbound, session),
                clientSeq,
                normalizedEditorId,
                normalizedEditorName,
                normalizedOperation,
                inbound.getTimestamp() == null ? LocalDateTime.now() : inbound.getTimestamp()
        );

        try {
            ingressPublisher.publish(ingressCommand);
        } catch (Exception ex) {
            throw new BusinessException("INGRESS_PUBLISH_FAILED", "failed to publish edit operation ingress command");
        }

        sendSafe(session, DocumentWsEvent.ackAcceptedByIngress(
                docId,
                clientSeq,
                baseRevision,
                normalizedEditorId,
                normalizedEditorName,
                toOperationView(normalizedOperation)
        ));
    }

    /**
     * SYNC_OPS: replay incremental operations after base revision.
     */
    private void handleSyncOps(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = requireDocumentId(inbound.getDocId());
        String normalizedEditorId = normalizeEditorId(inbound.getEditorId());
        permissionService.requireMember(docId, normalizedEditorId);

        int fromRevision = normalizeSyncBaseRevision(inbound.getBaseRevision());
        int limit = inbound.getSyncLimit() == null ? DEFAULT_SYNC_LIMIT : inbound.getSyncLimit();

        List<DocumentOperation> operations = operationService.listOperationsSince(docId, fromRevision, limit);
        for (DocumentOperation operation : operations) {
            sendSafe(session, DocumentWsEvent.applied(
                    docId,
                    operation.getId(),
                    operation.getRevisionNo(),
                    operation.getBaseRevision(),
                    operation.getEditorId(),
                    operation.getEditorName(),
                    toOperationView(operation)
            ));
        }

        CollaborativeDocument latest = documentService.getDocument(docId);
        sendSafe(session, DocumentWsEvent.syncDone(
                docId,
                fromRevision,
                latest.getLatestRevision(),
                operations.size()
        ));
    }

    /**
     * CURSOR_MOVE: broadcast cursor/selection change without persistence.
     */
    private void handleCursorMove(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = requireDocumentId(inbound.getDocId());
        String normalizedEditorId = normalizeEditorId(inbound.getEditorId());
        permissionService.requireMember(docId, normalizedEditorId);

        sessionRegistry.bind(docId, session);
        presenceService.join(docId, session.getId(), inbound.getEditorName());

        DocumentWsCursor cursor = normalizeCursor(inbound.getCursor());
        DocumentWsEvent event = DocumentWsEvent.cursorMoved(
                docId,
                normalizedEditorId,
                normalizeEditorName(inbound.getEditorName()),
                cursor
        );
        broadcast(docId, event, session.getId());
    }

    private void handleConflict(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = inbound.getDocId();
        if (docId == null || docId <= 0) {
            sendSafe(session, DocumentWsEvent.error(docId, "invalid documentId", inbound.getClientSeq()));
            return;
        }
        CollaborativeDocument latest = documentService.getDocument(docId);
        sendSafe(session, DocumentWsEvent.nackConflict(
                docId,
                inbound.getClientSeq(),
                latest.getLatestRevision(),
                latest.getTitle(),
                latest.getContent()
        ));
    }

    private Long requireDocumentId(Long docId) {
        if (docId == null || docId <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "docId must be > 0");
        }
        return docId;
    }

    private Integer normalizeSyncBaseRevision(Integer baseRevision) {
        if (baseRevision == null || baseRevision < 0) {
            throw new BusinessException("INVALID_ARGUMENT", "baseRevision must be >= 0 for sync");
        }
        return baseRevision;
    }

    private Integer requireEditBaseRevision(Integer baseRevision) {
        if (baseRevision == null || baseRevision <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "baseRevision must be >= 1 for edit");
        }
        return baseRevision;
    }

    private Long requireClientSeq(Long clientSeq) {
        if (clientSeq == null || clientSeq <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "clientSeq must be > 0");
        }
        return clientSeq;
    }

    private DocumentWsOperation normalizeEditOperation(DocumentWsOperation op) {
        if (op == null || op.getOpType() == null) {
            throw new BusinessException("INVALID_ARGUMENT", "operation type is required");
        }
        if (op.getPosition() == null || op.getPosition() < 0) {
            throw new BusinessException("INVALID_ARGUMENT", "operation position must be >= 0");
        }
        if (op.getOpType() == DocumentOpType.DELETE && (op.getLength() == null || op.getLength() <= 0)) {
            throw new BusinessException("INVALID_ARGUMENT", "operation length must be > 0 for delete");
        }
        if (op.getOpType() == DocumentOpType.REPLACE && (op.getLength() == null || op.getLength() < 0)) {
            throw new BusinessException("INVALID_ARGUMENT", "operation length must be >= 0 for replace");
        }
        if (op.getOpType() == DocumentOpType.INSERT && op.getText() == null) {
            throw new BusinessException("INVALID_ARGUMENT", "insert text must not be null");
        }
        if (op.getOpType() == DocumentOpType.REPLACE && op.getText() == null) {
            throw new BusinessException("INVALID_ARGUMENT", "replace text must not be null");
        }
        if (op.getLength() == null) {
            op.setLength(0);
        }
        return op;
    }

    private DocumentWsCursor normalizeCursor(DocumentWsCursor cursor) {
        if (cursor == null) {
            throw new BusinessException("INVALID_ARGUMENT", "cursor is required for CURSOR_MOVE");
        }
        Integer start = cursor.getStart();
        Integer end = cursor.getEnd();
        if (start == null || end == null || start < 0 || end < 0 || end < start) {
            throw new BusinessException("INVALID_ARGUMENT", "cursor range is invalid");
        }
        return cursor;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEditorId(String editorId) {
        if (editorId == null || editorId.isBlank()) {
            return "anonymous";
        }
        return editorId.trim();
    }

    private String normalizeEditorName(String editorName) {
        if (editorName == null || editorName.isBlank()) {
            return "anonymous";
        }
        return editorName.trim();
    }

    private String resolveLogicalSessionId(DocumentWsMessage inbound, WebSocketSession session) {
        if (inbound != null && inbound.getClientSessionId() != null && !inbound.getClientSessionId().isBlank()) {
            return inbound.getClientSessionId().trim();
        }
        return session.getId();
    }

    private DocumentWsOperation toOperationView(DocumentOperation op) {
        DocumentWsOperation view = new DocumentWsOperation();
        view.setOpType(op.getOpType());
        view.setPosition(op.getOpPosition());
        view.setLength(op.getOpLength());
        view.setText(op.getOpText());
        return view;
    }

    private DocumentWsOperation toOperationView(DocumentWsOperation op) {
        DocumentWsOperation view = new DocumentWsOperation();
        view.setOpType(op.getOpType());
        view.setPosition(op.getPosition());
        view.setLength(op.getLength());
        view.setText(op.getText());
        return view;
    }

    private void broadcast(Long docId, DocumentWsEvent event, String excludeSessionId) {
        for (WebSocketSession target : sessionRegistry.sessionsOf(docId)) {
            if (excludeSessionId != null && excludeSessionId.equals(target.getId())) {
                continue;
            }
            sendSafe(target, event);
        }
    }

    private void sendSafe(WebSocketSession session, DocumentWsEvent event) {
        WebSocketSession target = sessionRegistry.resolveSendSession(session);
        if (target == null || !target.isOpen()) {
            return;
        }
        try {
            target.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (IOException ex) {
            log.warn("websocket send failed, sessionId={}, eventType={}", target.getId(), event.type(), ex);
        }
    }
}

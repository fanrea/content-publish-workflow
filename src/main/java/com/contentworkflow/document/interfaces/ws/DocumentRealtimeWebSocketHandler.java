package com.contentworkflow.document.interfaces.ws;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.contentworkflow.document.application.ingress.DocumentOperationIngressPublisher;
import com.contentworkflow.document.application.realtime.DocumentRealtimeCrossGatewayBroadcaster;
import com.contentworkflow.document.application.realtime.DocumentRealtimeGatewayFacade;
import com.contentworkflow.document.application.realtime.DocumentRealtimePresenceService;
import com.contentworkflow.document.application.realtime.DocumentRealtimeSessionRegistry;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native WebSocket handler (without STOMP).
 */
@Component
public class DocumentRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentRealtimeWebSocketHandler.class);
    private static final int DEFAULT_SYNC_LIMIT = 200;
    private static final int MAX_BASE_VECTOR_ENTRIES = 128;
    private static final int MAX_BASE_VECTOR_ACTOR_ID_LENGTH = 128;
    private static final int MAX_DELTA_BATCH_ID_LENGTH = 128;
    private static final String DELTA_BATCH_SESSION_PREFIX = "delta-batch:";
    private static final String PRESENCE_NAMES_SESSION_KEY = "realtime:presence:names";
    private static final String REVISION_BY_DOC_SESSION_KEY = "realtime:revision:by-doc";
    private static final String GAP_INSTRUCTION_BY_DOC_SESSION_KEY = "realtime:pull-required:by-doc";

    private final ObjectMapper objectMapper;
    private final DocumentOperationIngressPublisher ingressPublisher;
    private final DocumentRealtimeCrossGatewayBroadcaster crossGatewayBroadcaster;
    private final DocumentRealtimeGatewayFacade gatewayFacade;
    private final DocumentRealtimePresenceService presenceService;
    private final DocumentRealtimeSessionRegistry sessionRegistry;

    public DocumentRealtimeWebSocketHandler(ObjectMapper objectMapper,
                                            DocumentOperationIngressPublisher ingressPublisher,
                                            DocumentRealtimeCrossGatewayBroadcaster crossGatewayBroadcaster,
                                            DocumentRealtimeGatewayFacade gatewayFacade,
                                            DocumentRealtimePresenceService presenceService,
                                            DocumentRealtimeSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.ingressPublisher = ingressPublisher;
        this.crossGatewayBroadcaster = crossGatewayBroadcaster;
        this.gatewayFacade = gatewayFacade;
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
        clearAllTrackedPresenceNames(session);
        clearAllTrackedRevisionState(session);
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
        String normalizedEditorId = normalizeEditorId(inbound.getEditorId());
        DocumentRealtimeGatewayFacade.JoinDecision joinDecision = gatewayFacade.prepareJoin(docId, normalizedEditorId);

        sessionRegistry.bind(docId, session);
        List<String> participantsBefore = presenceService.listParticipants(docId);
        List<String> participants = joinPresenceIfNeeded(docId, session, inbound.getEditorName());
        if (participants == null) {
            participants = participantsBefore;
        }

        if (joinDecision.snapshotAvailable()) {
            sendSafe(session, DocumentWsEvent.snapshot(
                    docId,
                    joinDecision.revision(),
                    joinDecision.title(),
                    joinDecision.content()
            ));
            rememberRevision(session, docId, joinDecision.revision());
        } else if (joinDecision.requiresInstruction()) {
            sendSafe(session, instructionEvent(
                    joinDecision.instructionType(),
                    joinDecision.instructionMessage(),
                    docId,
                    inbound.getClientSeq(),
                    null,
                    null
            ));
        } else {
            sendSafe(session, DocumentWsEvent.error(docId, "join decision is incomplete", inbound.getClientSeq()));
        }
        if (!participantsBefore.equals(participants)) {
            broadcast(docId, DocumentWsEvent.presence(docId, participants, "participant joined"), null);
        }
    }

    private void handleLeave(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = requireDocumentId(inbound.getDocId());
        List<String> participantsBefore = presenceService.listParticipants(docId);
        sessionRegistry.unbind(docId, session.getId());
        List<String> participants = presenceService.leave(docId, session.getId());
        clearTrackedPresenceName(session, docId);
        clearTrackedRevisionState(session, docId);
        if (!participantsBefore.equals(participants)) {
            broadcast(docId, DocumentWsEvent.presence(docId, participants, "participant left"), null);
        }
    }

    private void handleEdit(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = requireDocumentId(inbound.getDocId());
        Integer baseRevision = requireEditBaseRevision(inbound.getBaseRevision());
        Long clientSeq = requireClientSeq(inbound.getClientSeq());
        String normalizedDeltaBatchId = normalizeOptionalDeltaBatchId(inbound.getDeltaBatchId());
        Long normalizedClientClock = normalizeOptionalClientClock(inbound.getClientClock());
        Map<String, Long> normalizedBaseVector = normalizeOptionalBaseVector(inbound.getBaseVector());
        String normalizedEditorId = normalizeEditorId(inbound.getEditorId());
        String normalizedEditorName = normalizeEditorName(inbound.getEditorName());
        DocumentWsOperation normalizedOperation = normalizeEditOperation(inbound.getOp());

        gatewayFacade.authorizeCanEdit(docId, normalizedEditorId);

        sessionRegistry.bind(docId, session);
        joinPresenceIfNeeded(docId, session, normalizedEditorName);
        if (normalizedClientClock != null) {
            presenceService.upsertSessionClock(docId, session.getId(), normalizedClientClock);
        }

        DocumentOperationIngressCommand ingressCommand = new DocumentOperationIngressCommand(
                docId,
                baseRevision,
                resolveIdempotencySessionId(inbound, session, normalizedDeltaBatchId),
                clientSeq,
                normalizedEditorId,
                normalizedEditorName,
                normalizedOperation,
                inbound.getTimestamp() == null ? LocalDateTime.now() : inbound.getTimestamp(),
                normalizedDeltaBatchId,
                normalizedClientClock,
                normalizedBaseVector
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
        String normalizedEditorName = normalizeEditorName(inbound.getEditorName());
        int fromRevision = normalizeSyncBaseRevision(inbound.getBaseRevision());
        int limit = inbound.getSyncLimit() == null ? DEFAULT_SYNC_LIMIT : inbound.getSyncLimit();
        DocumentRealtimeGatewayFacade.SyncDecision syncDecision =
                gatewayFacade.prepareSync(docId, normalizedEditorId, fromRevision, limit);

        sessionRegistry.bind(docId, session);
        joinPresenceIfNeeded(docId, session, normalizedEditorName);

        if (syncDecision.requiresInstruction()) {
            sendSafe(session, instructionEvent(
                    syncDecision.instructionType(),
                    syncDecision.instructionMessage(),
                    docId,
                    inbound.getClientSeq(),
                    fromRevision,
                    syncDecision.latestRevision()
            ));
            return;
        }

        int latestRevision = syncDecision.latestRevision();
        List<DocumentOperation> operations = syncDecision.operations();
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
            rememberRevision(session, docId, operation.getRevisionNo());
        }

        sendSafe(session, DocumentWsEvent.syncDone(
                docId,
                fromRevision,
                latestRevision,
                operations.size()
        ));
        rememberRevision(session, docId, latestRevision);
    }

    /**
     * CURSOR_MOVE: broadcast cursor/selection change without persistence.
     */
    private void handleCursorMove(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = requireDocumentId(inbound.getDocId());
        String normalizedEditorId = normalizeEditorId(inbound.getEditorId());
        gatewayFacade.authorizeMember(docId, normalizedEditorId);

        sessionRegistry.bind(docId, session);
        joinPresenceIfNeeded(docId, session, inbound.getEditorName());

        DocumentWsCursor cursor = normalizeCursor(inbound.getCursor());
        DocumentWsEvent event = DocumentWsEvent.cursorMoved(
                docId,
                normalizedEditorId,
                normalizeEditorName(inbound.getEditorName()),
                cursor
        );
        broadcast(docId, event, session.getId());
        publishCrossGateway(event);
    }

    private void handleConflict(WebSocketSession session, DocumentWsMessage inbound) {
        Long docId = inbound.getDocId();
        if (docId == null || docId <= 0) {
            sendSafe(session, DocumentWsEvent.error(docId, "invalid documentId", inbound.getClientSeq()));
            return;
        }
        DocumentRealtimeGatewayFacade.ConflictDecision decision = gatewayFacade.resolveConflict(docId);
        if (decision.snapshotAvailable()) {
            sendSafe(session, DocumentWsEvent.nackConflict(
                    docId,
                    inbound.getClientSeq(),
                    decision.latestRevision(),
                    decision.title(),
                    decision.content()
            ));
            rememberRevision(session, docId, decision.latestRevision());
            return;
        }
        if (decision.requiresInstruction()) {
            sendSafe(session, instructionEvent(
                    decision.instructionType(),
                    decision.instructionMessage(),
                    docId,
                    inbound.getClientSeq(),
                    null,
                    decision.latestRevision()
            ));
            return;
        }
        sendSafe(session, DocumentWsEvent.error(docId, "conflict decision is incomplete", inbound.getClientSeq()));
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

    private String normalizeOptionalDeltaBatchId(String deltaBatchId) {
        if (deltaBatchId == null) {
            return null;
        }
        String normalized = deltaBatchId.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("INVALID_ARGUMENT", "deltaBatchId must not be blank");
        }
        if (normalized.length() > MAX_DELTA_BATCH_ID_LENGTH) {
            throw new BusinessException("INVALID_ARGUMENT", "deltaBatchId is too long");
        }
        return normalized;
    }

    private Long normalizeOptionalClientClock(Long clientClock) {
        if (clientClock == null) {
            return null;
        }
        if (clientClock <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "clientClock must be > 0");
        }
        return clientClock;
    }

    private Map<String, Long> normalizeOptionalBaseVector(Map<String, Long> baseVector) {
        if (baseVector == null || baseVector.isEmpty()) {
            return baseVector;
        }
        if (baseVector.size() > MAX_BASE_VECTOR_ENTRIES) {
            throw new BusinessException("INVALID_ARGUMENT", "baseVector size exceeds limit");
        }
        for (Map.Entry<String, Long> entry : baseVector.entrySet()) {
            String actorId = entry.getKey();
            Long clock = entry.getValue();
            if (actorId == null || actorId.isBlank()) {
                throw new BusinessException("INVALID_ARGUMENT", "baseVector actor id must not be blank");
            }
            if (actorId.length() > MAX_BASE_VECTOR_ACTOR_ID_LENGTH) {
                throw new BusinessException("INVALID_ARGUMENT", "baseVector actor id is too long");
            }
            if (clock == null || clock < 0) {
                throw new BusinessException("INVALID_ARGUMENT", "baseVector clock must be >= 0");
            }
        }
        return baseVector;
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

    private String resolveIdempotencySessionId(DocumentWsMessage inbound,
                                               WebSocketSession session,
                                               String normalizedDeltaBatchId) {
        if (normalizedDeltaBatchId != null) {
            return DELTA_BATCH_SESSION_PREFIX + normalizedDeltaBatchId;
        }
        return resolveLogicalSessionId(inbound, session);
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

    private void publishCrossGateway(DocumentWsEvent event) {
        try {
            crossGatewayBroadcaster.publish(event);
        } catch (Exception ex) {
            log.warn("cross-gateway publish failed from websocket handler, docId={}, eventType={}",
                    event == null ? null : event.docId(),
                    event == null ? null : event.type(),
                    ex);
        }
    }

    private List<String> joinPresenceIfNeeded(Long docId, WebSocketSession session, String editorName) {
        String normalizedEditorName = normalizeEditorName(editorName);
        Map<Long, String> trackedNames = trackedPresenceNames(session);
        String previousName = trackedNames.put(docId, normalizedEditorName);
        if (normalizedEditorName.equals(previousName)) {
            return null;
        }
        return presenceService.join(docId, session.getId(), normalizedEditorName);
    }

    private void clearTrackedPresenceName(WebSocketSession session, Long docId) {
        trackedPresenceNames(session).remove(docId);
    }

    private void clearAllTrackedPresenceNames(WebSocketSession session) {
        session.getAttributes().remove(PRESENCE_NAMES_SESSION_KEY);
    }

    private void rememberRevision(WebSocketSession session, Long docId, Integer revision) {
        if (docId == null || docId <= 0 || revision == null || revision < 0) {
            return;
        }
        trackedRevisionsByDoc(session).merge(docId, revision, Math::max);
        trackedGapInstructionsByDoc(session).remove(docId);
    }

    private void clearTrackedRevisionState(WebSocketSession session, Long docId) {
        if (docId == null || docId <= 0) {
            return;
        }
        trackedRevisionsByDoc(session).remove(docId);
        trackedGapInstructionsByDoc(session).remove(docId);
    }

    private void clearAllTrackedRevisionState(WebSocketSession session) {
        session.getAttributes().remove(REVISION_BY_DOC_SESSION_KEY);
        session.getAttributes().remove(GAP_INSTRUCTION_BY_DOC_SESSION_KEY);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, String> trackedPresenceNames(WebSocketSession session) {
        return (Map<Long, String>) session.getAttributes().computeIfAbsent(
                PRESENCE_NAMES_SESSION_KEY,
                key -> new ConcurrentHashMap<Long, String>()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> trackedRevisionsByDoc(WebSocketSession session) {
        return (Map<Long, Integer>) session.getAttributes().computeIfAbsent(
                REVISION_BY_DOC_SESSION_KEY,
                key -> new ConcurrentHashMap<Long, Integer>()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> trackedGapInstructionsByDoc(WebSocketSession session) {
        return (Map<Long, Integer>) session.getAttributes().computeIfAbsent(
                GAP_INSTRUCTION_BY_DOC_SESSION_KEY,
                key -> new ConcurrentHashMap<Long, Integer>()
        );
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

    private DocumentWsEvent instructionEvent(String instructionType,
                                             String instructionMessage,
                                             Long docId,
                                             Long clientSeq,
                                             Integer baseRevision,
                                             Integer latestRevision) {
        return new DocumentWsEvent(
                instructionType,
                instructionMessage,
                docId,
                null,
                clientSeq,
                null,
                baseRevision,
                latestRevision,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }
}

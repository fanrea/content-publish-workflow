package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.application.gc.CompactionPolicyEvaluator;
import com.contentworkflow.document.application.gc.DocumentCompactionTaskPublisher;
import com.contentworkflow.document.application.gc.TombstoneGcScheduler;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.entity.DocumentComment;
import com.contentworkflow.document.domain.entity.DocumentCommentReply;
import com.contentworkflow.document.interfaces.ws.DocumentWsComment;
import com.contentworkflow.document.interfaces.ws.DocumentWsCommentReply;
import com.contentworkflow.document.interfaces.ws.DocumentWsEvent;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

/**
 * 文档实时推送服务（供 HTTP 业务触发 WebSocket 广播）。
 */
@Service
public class DocumentRealtimePushService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRealtimePushService.class);

    private final DocumentRealtimeSessionRegistry sessionRegistry;
    private final DocumentRealtimeRecentUpdateCache recentUpdateCache;
    private final DocumentRealtimeCrossGatewayBroadcaster crossGatewayBroadcaster;
    private final CompactionPolicyEvaluator compactionPolicyEvaluator;
    private final DocumentCompactionTaskPublisher compactionTaskPublisher;
    private final ObjectMapper objectMapper;
    private TombstoneGcScheduler tombstoneGcScheduler;

    public DocumentRealtimePushService(DocumentRealtimeSessionRegistry sessionRegistry,
                                       DocumentRealtimeRecentUpdateCache recentUpdateCache,
                                       DocumentRealtimeCrossGatewayBroadcaster crossGatewayBroadcaster,
                                       CompactionPolicyEvaluator compactionPolicyEvaluator,
                                       DocumentCompactionTaskPublisher compactionTaskPublisher,
                                       ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.recentUpdateCache = recentUpdateCache;
        this.crossGatewayBroadcaster = crossGatewayBroadcaster;
        this.compactionPolicyEvaluator = compactionPolicyEvaluator;
        this.compactionTaskPublisher = compactionTaskPublisher;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setTombstoneGcScheduler(TombstoneGcScheduler tombstoneGcScheduler) {
        this.tombstoneGcScheduler = tombstoneGcScheduler;
    }

    /**
     * 广播评论新增事件。
     */
    public void broadcastCommentCreated(Long documentId, DocumentComment comment) {
        if (documentId == null || comment == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentCreated(documentId, toWsComment(comment)), true);
    }

    /**
     * 广播评论解决事件。
     */
    public void broadcastCommentResolved(Long documentId, DocumentComment comment) {
        if (documentId == null || comment == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentResolved(documentId, toWsComment(comment)), true);
    }

    /**
     * 广播评论重开事件。
     */
    public void broadcastCommentReopened(Long documentId, DocumentComment comment) {
        if (documentId == null || comment == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentReopened(documentId, toWsComment(comment)), true);
    }

    /**
     * 广播评论删除事件。
     */
    public void broadcastCommentDeleted(Long documentId, DocumentComment comment) {
        if (documentId == null || comment == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentDeleted(documentId, toWsComment(comment)), true);
    }

    /**
     * 广播评论回复新增事件。
     */
    public void broadcastCommentReplyCreated(Long documentId, DocumentCommentReply reply) {
        if (documentId == null || reply == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentReplyCreated(documentId, toWsReply(reply)), true);
    }

    public void broadcastOperationApplied(DocumentOperation operation) {
        if (operation == null || operation.getDocumentId() == null) {
            return;
        }
        try {
            recentUpdateCache.append(operation);
        } catch (Exception ex) {
            log.warn("recent update cache append failed unexpectedly, docId={}, revision={}",
                    operation.getDocumentId(),
                    operation.getRevisionNo(),
                    ex);
        }
        try {
            compactionPolicyEvaluator.onOperationApplied(operation)
                    .ifPresent(task -> {
                        try {
                            compactionTaskPublisher.publish(task);
                        } catch (Exception ex) {
                            log.warn("compaction task publish failed, docId={}, trigger={}",
                                    task.documentId(),
                                    task.trigger(),
                                    ex);
                        }
                    });
        } catch (Exception ex) {
            log.warn("compaction evaluate failed unexpectedly, docId={}, revision={}",
                    operation.getDocumentId(),
                    operation.getRevisionNo(),
                    ex);
        }
        tryScheduleTombstoneGc(operation);
        broadcast(operation.getDocumentId(), DocumentWsEvent.applied(
                operation.getDocumentId(),
                operation.getId(),
                operation.getRevisionNo(),
                operation.getBaseRevision(),
                operation.getEditorId(),
                operation.getEditorName(),
                toWsOperation(operation)
        ), true);
    }

    /**
     * Presence event is already delivered locally by websocket handler.
     * Here we only publish it to cross-gateway transport.
     */
    public void publishPresence(Long documentId, List<String> participants, String message) {
        if (documentId == null || documentId <= 0) {
            return;
        }
        publishCrossGateway(DocumentWsEvent.presence(documentId, participants, message));
    }

    void broadcastFromRemote(Long documentId, DocumentWsEvent event) {
        broadcast(documentId, event, false);
    }

    private void broadcast(Long documentId, DocumentWsEvent event, boolean publishCrossGateway) {
        if (documentId == null || event == null) {
            return;
        }
        for (WebSocketSession session : sessionRegistry.sessionsOf(documentId)) {
            sendSafe(session, event);
        }
        if (publishCrossGateway) {
            publishCrossGateway(event);
        }
    }

    private void publishCrossGateway(DocumentWsEvent event) {
        try {
            crossGatewayBroadcaster.publish(event);
        } catch (Exception ex) {
            log.warn("cross-gateway publish failed, docId={}, eventType={}", event.docId(), event.type(), ex);
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
            log.warn("websocket push failed, sessionId={}, eventType={}", target.getId(), event.type(), ex);
        }
    }

    private DocumentWsComment toWsComment(DocumentComment comment) {
        DocumentWsComment payload = new DocumentWsComment();
        payload.setId(comment.getId());
        payload.setStartOffset(comment.getStartOffset());
        payload.setEndOffset(comment.getEndOffset());
        payload.setContent(comment.getContent());
        payload.setStatus(comment.getStatus());
        payload.setCreatedById(comment.getCreatedById());
        payload.setCreatedByName(comment.getCreatedByName());
        payload.setCreatedAt(comment.getCreatedAt());
        payload.setResolvedById(comment.getResolvedById());
        payload.setResolvedByName(comment.getResolvedByName());
        payload.setResolvedAt(comment.getResolvedAt());
        return payload;
    }

    private DocumentWsCommentReply toWsReply(DocumentCommentReply reply) {
        DocumentWsCommentReply payload = new DocumentWsCommentReply();
        payload.setId(reply.getId());
        payload.setCommentId(reply.getCommentId());
        payload.setReplyToReplyId(reply.getReplyToReplyId());
        payload.setContent(reply.getContent());
        payload.setMentionMemberIds(reply.getMentionMemberIds());
        payload.setCreatedById(reply.getCreatedById());
        payload.setCreatedByName(reply.getCreatedByName());
        payload.setCreatedAt(reply.getCreatedAt());
        return payload;
    }

    private DocumentWsOperation toWsOperation(DocumentOperation operation) {
        DocumentWsOperation payload = new DocumentWsOperation();
        payload.setOpType(operation.getOpType());
        payload.setPosition(operation.getOpPosition());
        payload.setLength(operation.getOpLength());
        payload.setText(operation.getOpText());
        return payload;
    }

    private void tryScheduleTombstoneGc(DocumentOperation operation) {
        TombstoneGcScheduler scheduler = this.tombstoneGcScheduler;
        if (scheduler == null) {
            return;
        }
        Long upperClock = resolveUpperClock(operation);
        if (upperClock == null || upperClock <= 0L) {
            return;
        }
        try {
            scheduler.schedule(operation.getDocumentId(), upperClock);
        } catch (Exception ex) {
            log.warn("tombstone gc schedule failed, docId={}, upperClock={}",
                    operation.getDocumentId(),
                    upperClock,
                    ex);
        }
    }

    private Long resolveUpperClock(DocumentOperation operation) {
        if (operation == null) {
            return null;
        }
        Integer revisionNo = operation.getRevisionNo();
        if (revisionNo != null && revisionNo > 0) {
            return revisionNo.longValue();
        }
        Long operationId = operation.getId();
        if (operationId != null && operationId > 0L) {
            return operationId;
        }
        Integer baseRevision = operation.getBaseRevision();
        if (baseRevision != null && baseRevision > 0) {
            return baseRevision.longValue();
        }
        return null;
    }
}

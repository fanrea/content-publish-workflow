package com.contentworkflow.document.application.realtime;

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
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * 文档实时推送服务（供 HTTP 业务触发 WebSocket 广播）。
 */
@Service
public class DocumentRealtimePushService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRealtimePushService.class);

    private final DocumentRealtimeSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public DocumentRealtimePushService(DocumentRealtimeSessionRegistry sessionRegistry,
                                       ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 广播评论新增事件。
     */
    public void broadcastCommentCreated(Long documentId, DocumentComment comment) {
        if (documentId == null || comment == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentCreated(documentId, toWsComment(comment)));
    }

    /**
     * 广播评论解决事件。
     */
    public void broadcastCommentResolved(Long documentId, DocumentComment comment) {
        if (documentId == null || comment == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentResolved(documentId, toWsComment(comment)));
    }

    /**
     * 广播评论重开事件。
     */
    public void broadcastCommentReopened(Long documentId, DocumentComment comment) {
        if (documentId == null || comment == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentReopened(documentId, toWsComment(comment)));
    }

    /**
     * 广播评论删除事件。
     */
    public void broadcastCommentDeleted(Long documentId, DocumentComment comment) {
        if (documentId == null || comment == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentDeleted(documentId, toWsComment(comment)));
    }

    /**
     * 广播评论回复新增事件。
     */
    public void broadcastCommentReplyCreated(Long documentId, DocumentCommentReply reply) {
        if (documentId == null || reply == null) {
            return;
        }
        broadcast(documentId, DocumentWsEvent.commentReplyCreated(documentId, toWsReply(reply)));
    }

    public void broadcastOperationApplied(DocumentOperation operation) {
        if (operation == null || operation.getDocumentId() == null) {
            return;
        }
        broadcast(operation.getDocumentId(), DocumentWsEvent.applied(
                operation.getDocumentId(),
                operation.getId(),
                operation.getRevisionNo(),
                operation.getBaseRevision(),
                operation.getEditorId(),
                operation.getEditorName(),
                toWsOperation(operation)
        ));
    }

    private void broadcast(Long documentId, DocumentWsEvent event) {
        for (WebSocketSession session : sessionRegistry.sessionsOf(documentId)) {
            sendSafe(session, event);
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
}

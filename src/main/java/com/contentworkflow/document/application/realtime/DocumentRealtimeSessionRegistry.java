package com.contentworkflow.document.application.realtime;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档会话注册中心：
 * 维护“文档 -> 会话”和“会话 -> 文档”的双向关系，便于广播与断线清理。
 */
@Service
public class DocumentRealtimeSessionRegistry {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final Map<Long, Map<String, WebSocketSession>> sessionsByDocument = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> documentsBySession = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sendSessionsById = new ConcurrentHashMap<>();

    /**
     * 把会话绑定到某个文档房间。
     */
    public void bind(Long documentId, WebSocketSession session) {
        if (documentId == null || documentId <= 0 || session == null || session.getId() == null) {
            return;
        }
        String sessionId = session.getId();
        WebSocketSession safeSession = resolveSendSession(session);
        sessionsByDocument
                .computeIfAbsent(documentId, key -> new ConcurrentHashMap<>())
                .compute(sessionId, (key, existing) -> {
                    if (existing != null && existing.isOpen()) {
                        return existing;
                    }
                    return safeSession;
                });
        documentsBySession
                .computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet())
                .add(documentId);
    }

    /**
     * 把会话从某个文档房间解绑。
     */
    public void unbind(Long documentId, String sessionId) {
        if (documentId == null || documentId <= 0 || sessionId == null || sessionId.isBlank()) {
            return;
        }
        Map<String, WebSocketSession> sessions = sessionsByDocument.get(documentId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                sessionsByDocument.remove(documentId);
            }
        }
        Set<Long> docs = documentsBySession.get(sessionId);
        if (docs != null) {
            docs.remove(documentId);
            if (docs.isEmpty()) {
                documentsBySession.remove(sessionId);
                sendSessionsById.remove(sessionId);
            }
        }
    }

    /**
     * 会话断开时，移除该会话在全部文档中的绑定关系。
     * @return 受影响的文档 ID 列表
     */
    public List<Long> removeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        sendSessionsById.remove(sessionId);
        Set<Long> docs = documentsBySession.remove(sessionId);
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<Long> affected = new ArrayList<>(docs.size());
        for (Long docId : docs) {
            Map<String, WebSocketSession> sessions = sessionsByDocument.get(docId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    sessionsByDocument.remove(docId);
                }
            }
            affected.add(docId);
        }
        return affected;
    }

    /**
     * 获取某文档房间内全部会话快照。
     */
    public Collection<WebSocketSession> sessionsOf(Long documentId) {
        Map<String, WebSocketSession> sessions = sessionsByDocument.get(documentId);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return List.copyOf(sessions.values());
    }

    /**
     * 鑾峰彇鍙畨鍏ㄥ苟鍙戦€佺殑浼氳瘽瀵硅薄銆?     */
    public WebSocketSession resolveSendSession(WebSocketSession session) {
        if (session == null || session.getId() == null || session.getId().isBlank()) {
            return session;
        }
        String sessionId = session.getId();
        return sendSessionsById.compute(sessionId, (key, existing) -> {
            if (existing != null && existing.isOpen()) {
                return existing;
            }
            return decorateSession(session);
        });
    }

    private WebSocketSession decorateSession(WebSocketSession session) {
        if (session instanceof ConcurrentWebSocketSessionDecorator) {
            return session;
        }
        return new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT_BYTES
        );
    }
}

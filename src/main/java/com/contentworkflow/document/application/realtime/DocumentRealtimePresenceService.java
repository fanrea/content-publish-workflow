package com.contentworkflow.document.application.realtime;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DocumentRealtimePresenceService 服务类，负责封装核心业务逻辑和状态流转。
 */
@Service
public class DocumentRealtimePresenceService {

    private final Map<Long, Map<String, String>> participantsByDocument = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> documentsBySession = new ConcurrentHashMap<>();
    private final DocumentRealtimeRedisIndex redisIndex;

    public DocumentRealtimePresenceService(DocumentRealtimeRedisIndex redisIndex) {
        this.redisIndex = redisIndex;
    }

    /**
     * 加入协作会话。
     * @param documentId 参数 documentId。
     * @param sessionId 参数 sessionId。
     * @param editorName 参数 editorName。
     * @return 方法执行后的结果对象。
     */
    public List<String> join(Long documentId, String sessionId, String editorName) {
        if (!isValid(documentId) || sessionId == null || sessionId.isBlank()) {
            return listParticipants(documentId);
        }
        String normalizedName = normalizeEditorName(editorName);
        String previousName = participantsByDocument
                .computeIfAbsent(documentId, key -> new ConcurrentHashMap<>())
                .put(sessionId, normalizedName);
        documentsBySession
                .computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet())
                .add(documentId);
        if (previousName == null) {
            redisIndex.incrementOnlineUser(documentId, normalizedName);
        } else if (!previousName.equals(normalizedName)) {
            redisIndex.decrementOnlineUser(documentId, previousName);
            redisIndex.incrementOnlineUser(documentId, normalizedName);
        }
        return listParticipants(documentId);
    }

    /**
     * 离开协作会话。
     * @param documentId 参数 documentId。
     * @param sessionId 参数 sessionId。
     * @return 方法执行后的结果对象。
     */
    public List<String> leave(Long documentId, String sessionId) {
        if (!isValid(documentId) || sessionId == null || sessionId.isBlank()) {
            return listParticipants(documentId);
        }
        String removedName = removeSessionFromDocument(documentId, sessionId);
        Set<Long> docs = documentsBySession.get(sessionId);
        if (docs != null) {
            docs.remove(documentId);
            if (docs.isEmpty()) {
                documentsBySession.remove(sessionId);
            }
        }
        if (removedName != null) {
            redisIndex.decrementOnlineUser(documentId, removedName);
        }
        redisIndex.removeSessionClock(documentId, sessionId);
        return listParticipants(documentId);
    }

    /**
     * 连接断开时，清理其参与的全部文档会话。
     * @param sessionId 参数 sessionId。
     * @return 方法执行后的结果对象。
     */
    public List<Long> removeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        Set<Long> docs = documentsBySession.remove(sessionId);
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<Long> affected = new ArrayList<>(docs.size());
        for (Long docId : docs) {
            String removedName = removeSessionFromDocument(docId, sessionId);
            if (removedName != null) {
                redisIndex.decrementOnlineUser(docId, removedName);
            }
            redisIndex.removeSessionClock(docId, sessionId);
            affected.add(docId);
        }
        return affected;
    }

    public void upsertSessionClock(Long documentId, String sessionId, Long clock) {
        if (!isValid(documentId) || sessionId == null || sessionId.isBlank() || clock == null || clock <= 0L) {
            return;
        }
        redisIndex.upsertSessionClock(documentId, sessionId, clock);
    }

    /**
     * 获取当前在线协作者列表。
     * @param documentId 参数 documentId。
     * @return 方法执行后的结果对象。
     */
    public List<String> listParticipants(Long documentId) {
        if (!isValid(documentId)) {
            return List.of();
        }
        Map<String, String> participants = participantsByDocument.get(documentId);
        if (participants == null || participants.isEmpty()) {
            return List.of();
        }
        return participants.values().stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * 处理 removeSessionFromDocument 相关业务逻辑。
     * @param documentId 参数 documentId。
     * @param sessionId 参数 sessionId。
     */
    private String removeSessionFromDocument(Long documentId, String sessionId) {
        Map<String, String> participants = participantsByDocument.get(documentId);
        if (participants == null) {
            return null;
        }
        String removedName = participants.remove(sessionId);
        if (participants.isEmpty()) {
            participantsByDocument.remove(documentId);
        }
        return removedName;
    }

    /**
     * 判断当前条件是否成立。
     * @param documentId 参数 documentId。
     * @return 条件成立返回 true，否则返回 false。
     */
    private boolean isValid(Long documentId) {
        return documentId != null && documentId > 0;
    }

    /**
     * 规范化输入参数，统一后续处理。
     * @param editorName 参数 editorName。
     * @return 方法执行后的结果对象。
     */
    private String normalizeEditorName(String editorName) {
        if (editorName == null || editorName.isBlank()) {
            return "anonymous";
        }
        return editorName.trim();
    }
}

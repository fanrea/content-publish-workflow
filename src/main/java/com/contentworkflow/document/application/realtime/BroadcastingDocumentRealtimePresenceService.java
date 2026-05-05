package com.contentworkflow.document.application.realtime;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Presence service wrapper that forwards presence changes to cross-gateway transport.
 */
@Service
@Primary
public class BroadcastingDocumentRealtimePresenceService extends DocumentRealtimePresenceService {

    private final DocumentRealtimePushService pushService;

    public BroadcastingDocumentRealtimePresenceService(DocumentRealtimeRedisIndex redisIndex,
                                                       DocumentRealtimePushService pushService) {
        super(redisIndex);
        this.pushService = pushService;
    }

    @Override
    public List<String> join(Long documentId, String sessionId, String editorName) {
        List<String> before = listParticipants(documentId);
        List<String> participants = super.join(documentId, sessionId, editorName);
        if (isValidDocumentId(documentId) && !before.equals(participants)) {
            pushService.publishPresence(documentId, participants, "participant joined");
        }
        return participants;
    }

    @Override
    public List<String> leave(Long documentId, String sessionId) {
        List<String> before = listParticipants(documentId);
        List<String> participants = super.leave(documentId, sessionId);
        if (isValidDocumentId(documentId) && !before.equals(participants)) {
            pushService.publishPresence(documentId, participants, "participant left");
        }
        return participants;
    }

    @Override
    public List<Long> removeSession(String sessionId) {
        List<Long> affectedDocs = super.removeSession(sessionId);
        for (Long docId : affectedDocs) {
            if (isValidDocumentId(docId)) {
                pushService.publishPresence(docId, listParticipants(docId), "participant disconnected");
            }
        }
        return affectedDocs;
    }

    private boolean isValidDocumentId(Long documentId) {
        return documentId != null && documentId > 0;
    }
}

package com.contentworkflow.document.application.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.OptionalLong;

@Service
@ConditionalOnMissingBean(DocumentRealtimeRedisIndex.class)
public class NoopDocumentRealtimeRedisIndex implements DocumentRealtimeRedisIndex {

    @Override
    public void addRoomGateway(Long documentId) {
        // no-op
    }

    @Override
    public void removeRoomGateway(Long documentId) {
        // no-op
    }

    @Override
    public void addGatewaySession(String sessionId) {
        // no-op
    }

    @Override
    public void removeGatewaySession(String sessionId) {
        // no-op
    }

    @Override
    public void incrementOnlineUser(Long documentId, String userId) {
        // no-op
    }

    @Override
    public void decrementOnlineUser(Long documentId, String userId) {
        // no-op
    }

    @Override
    public void upsertSessionClock(Long documentId, String sessionId, long clock) {
        // no-op
    }

    @Override
    public void removeSessionClock(Long documentId, String sessionId) {
        // no-op
    }

    @Override
    public OptionalLong minimumSessionClock(Long documentId) {
        return OptionalLong.empty();
    }
}

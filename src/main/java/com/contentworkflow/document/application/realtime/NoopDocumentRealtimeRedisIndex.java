package com.contentworkflow.document.application.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

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
}

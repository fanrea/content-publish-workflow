package com.contentworkflow.document.application.realtime;

import java.util.OptionalLong;

/**
 * Redis index abstraction for realtime gateway routing and presence.
 */
public interface DocumentRealtimeRedisIndex {

    void addRoomGateway(Long documentId);

    void removeRoomGateway(Long documentId);

    void addGatewaySession(String sessionId);

    void removeGatewaySession(String sessionId);

    void incrementOnlineUser(Long documentId, String userId);

    void decrementOnlineUser(Long documentId, String userId);

    void upsertSessionClock(Long documentId, String sessionId, long clock);

    void removeSessionClock(Long documentId, String sessionId);

    OptionalLong minimumSessionClock(Long documentId);
}

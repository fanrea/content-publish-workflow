package com.contentworkflow.document.application.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRealtimePresenceServiceTest {

    private DocumentRealtimePresenceService service;
    private RecordingRedisIndex redisIndex;

    @BeforeEach
    void setUp() {
        redisIndex = new RecordingRedisIndex();
        service = new DocumentRealtimePresenceService(redisIndex);
    }

    @Test
    void joinAndLeave_shouldKeepDistinctSortedParticipants() {
        service.join(100L, "s-1", "bob");
        service.join(100L, "s-2", "alice");
        service.join(100L, "s-3", "alice");

        assertThat(service.listParticipants(100L)).containsExactly("alice", "bob");

        service.leave(100L, "s-2");
        assertThat(service.listParticipants(100L)).containsExactly("alice", "bob");

        service.leave(100L, "s-3");
        assertThat(service.listParticipants(100L)).containsExactly("bob");
    }

    @Test
    void removeSession_shouldCleanUpAcrossAllJoinedDocuments() {
        service.join(100L, "s-1", "alice");
        service.join(200L, "s-1", "alice");
        service.join(100L, "s-2", "bob");

        List<Long> affected = service.removeSession("s-1");

        assertThat(affected).containsExactlyInAnyOrder(100L, 200L);
        assertThat(service.listParticipants(100L)).containsExactly("bob");
        assertThat(service.listParticipants(200L)).isEmpty();
    }

    @Test
    void joinLeaveAndRemove_shouldMaintainRedisOnlineUserCount() {
        service.join(100L, "s-1", "alice");
        service.join(100L, "s-2", "alice");
        service.join(100L, "s-3", "bob");

        assertThat(redisIndex.onlineUserCount(100L, "alice")).isEqualTo(2);
        assertThat(redisIndex.onlineUserCount(100L, "bob")).isEqualTo(1);

        service.leave(100L, "s-1");
        assertThat(redisIndex.onlineUserCount(100L, "alice")).isEqualTo(1);

        service.removeSession("s-2");
        assertThat(redisIndex.onlineUserCount(100L, "alice")).isZero();
        assertThat(redisIndex.onlineUserCount(100L, "bob")).isEqualTo(1);
    }

    @Test
    void join_shouldAdjustCountWhenSameSessionEditorChanges() {
        service.join(100L, "s-1", "alice");
        service.join(100L, "s-1", "bob");

        assertThat(redisIndex.onlineUserCount(100L, "alice")).isZero();
        assertThat(redisIndex.onlineUserCount(100L, "bob")).isEqualTo(1);
        assertThat(service.listParticipants(100L)).containsExactly("bob");
    }

    private static final class RecordingRedisIndex implements DocumentRealtimeRedisIndex {
        private final Map<String, Integer> onlineUserCounter = new HashMap<>();

        @Override
        public void addRoomGateway(Long documentId) {
            // no-op for this test
        }

        @Override
        public void removeRoomGateway(Long documentId) {
            // no-op for this test
        }

        @Override
        public void addGatewaySession(String sessionId) {
            // no-op for this test
        }

        @Override
        public void removeGatewaySession(String sessionId) {
            // no-op for this test
        }

        @Override
        public void incrementOnlineUser(Long documentId, String userId) {
            String key = key(documentId, userId);
            onlineUserCounter.put(key, onlineUserCounter.getOrDefault(key, 0) + 1);
        }

        @Override
        public void decrementOnlineUser(Long documentId, String userId) {
            String key = key(documentId, userId);
            int next = onlineUserCounter.getOrDefault(key, 0) - 1;
            if (next <= 0) {
                onlineUserCounter.remove(key);
                return;
            }
            onlineUserCounter.put(key, next);
        }

        int onlineUserCount(Long documentId, String userId) {
            return onlineUserCounter.getOrDefault(key(documentId, userId), 0);
        }

        private String key(Long documentId, String userId) {
            return documentId + "|" + userId;
        }
    }
}

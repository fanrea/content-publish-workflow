package com.contentworkflow.document.application.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRealtimePresenceServiceTest {

    private DocumentRealtimePresenceService service;

    @BeforeEach
    void setUp() {
        service = new DocumentRealtimePresenceService();
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
}

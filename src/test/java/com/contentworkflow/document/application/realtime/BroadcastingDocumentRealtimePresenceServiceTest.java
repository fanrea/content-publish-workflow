package com.contentworkflow.document.application.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BroadcastingDocumentRealtimePresenceServiceTest {

    private BroadcastingDocumentRealtimePresenceService service;
    private DocumentRealtimePushService pushService;

    @BeforeEach
    void setUp() {
        pushService = mock(DocumentRealtimePushService.class);
        service = new BroadcastingDocumentRealtimePresenceService(new NoopDocumentRealtimeRedisIndex(), pushService);
    }

    @Test
    void join_shouldPublishPresenceToCrossGatewayBus() {
        service.join(100L, "s-1", "alice");

        verify(pushService, times(1)).publishPresence(eq(100L), eq(List.of("alice")), eq("participant joined"));
    }

    @Test
    void leave_shouldPublishPresenceToCrossGatewayBus() {
        service.join(100L, "s-1", "alice");

        service.leave(100L, "s-1");

        verify(pushService, times(1)).publishPresence(eq(100L), eq(List.of()), eq("participant left"));
    }

    @Test
    void removeSession_shouldPublishPresenceToCrossGatewayBusForEachAffectedDocument() {
        service.join(100L, "s-1", "alice");
        service.join(200L, "s-1", "alice");

        service.removeSession("s-1");

        verify(pushService, times(1)).publishPresence(eq(100L), eq(List.of()), eq("participant disconnected"));
        verify(pushService, times(1)).publishPresence(eq(200L), eq(List.of()), eq("participant disconnected"));
    }
}

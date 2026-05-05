package com.contentworkflow.document.application.gc;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TombstoneGcSchedulerTest {

    @Test
    void schedule_shouldPublishWhenWatermarkAllowsReclaim() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(50L));
        DocumentCompactionTaskPublisher publisher = mock(DocumentCompactionTaskPublisher.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-30T12:00:00Z"), ZoneId.of("UTC"));
        TombstoneGcScheduler scheduler = new TombstoneGcScheduler(decider, publisher, fixedClock);

        scheduler.schedule(100L, 40L);

        ArgumentCaptor<DocumentCompactionTask> taskCaptor = ArgumentCaptor.forClass(DocumentCompactionTask.class);
        verify(publisher, times(1)).publish(taskCaptor.capture());
        assertThat(taskCaptor.getValue().documentId()).isEqualTo(100L);
        assertThat(taskCaptor.getValue().trigger()).isEqualTo("TOMBSTONE_GC");
        assertThat(taskCaptor.getValue().segmentUpperClockInclusive()).isEqualTo(40L);
        assertThat(taskCaptor.getValue().createdAt()).isEqualTo(Instant.parse("2026-04-30T12:00:00Z"));
    }

    @Test
    void schedule_shouldNotPublishWhenWatermarkInsufficient() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(20L));
        DocumentCompactionTaskPublisher publisher = mock(DocumentCompactionTaskPublisher.class);
        TombstoneGcScheduler scheduler = new TombstoneGcScheduler(decider, publisher, Clock.systemUTC());

        scheduler.schedule(100L, 30L);

        verify(publisher, never()).publish(any(DocumentCompactionTask.class));
    }

    @Test
    void schedule_shouldNotPublishWhenWatermarkUnavailable() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.empty());
        DocumentCompactionTaskPublisher publisher = mock(DocumentCompactionTaskPublisher.class);
        TombstoneGcScheduler scheduler = new TombstoneGcScheduler(decider, publisher, Clock.systemUTC());

        scheduler.schedule(100L, 30L);

        verify(publisher, never()).publish(any(DocumentCompactionTask.class));
    }

    @Test
    void schedule_shouldNotPublishDuplicateUpperClockForSameDocument() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(100L));
        DocumentCompactionTaskPublisher publisher = mock(DocumentCompactionTaskPublisher.class);
        TombstoneGcScheduler scheduler = new TombstoneGcScheduler(decider, publisher, Clock.systemUTC());

        scheduler.schedule(100L, 50L);
        scheduler.schedule(100L, 50L);
        scheduler.schedule(100L, 40L);

        verify(publisher, times(1)).publish(any(DocumentCompactionTask.class));
    }

    @Test
    void schedule_shouldRollbackReservationWhenPublishFailsSoNextRetryCanProceed() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(100L));
        DocumentCompactionTaskPublisher publisher = mock(DocumentCompactionTaskPublisher.class);
        doThrow(new RuntimeException("mq unavailable"))
                .doNothing()
                .when(publisher)
                .publish(any(DocumentCompactionTask.class));
        TombstoneGcScheduler scheduler = new TombstoneGcScheduler(decider, publisher, Clock.systemUTC());

        try {
            scheduler.schedule(100L, 50L);
        } catch (RuntimeException ignored) {
            // expected for first attempt
        }
        scheduler.schedule(100L, 50L);

        verify(publisher, times(2)).publish(any(DocumentCompactionTask.class));
    }

    @Test
    void schedule_shouldNotPublishWhenUpperClockInvalid() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(100L));
        DocumentCompactionTaskPublisher publisher = mock(DocumentCompactionTaskPublisher.class);
        TombstoneGcScheduler scheduler = new TombstoneGcScheduler(decider, publisher, Clock.systemUTC());

        scheduler.schedule(100L, 0L);

        verify(publisher, never()).publish(any(DocumentCompactionTask.class));
    }

    @Test
    void schedule_shouldNotPublishWhenDocumentIdInvalid() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(100L));
        DocumentCompactionTaskPublisher publisher = mock(DocumentCompactionTaskPublisher.class);
        TombstoneGcScheduler scheduler = new TombstoneGcScheduler(decider, publisher, Clock.systemUTC());

        scheduler.schedule(null, 10L);
        scheduler.schedule(0L, 10L);

        verify(publisher, never()).publish(any(DocumentCompactionTask.class));
    }
}

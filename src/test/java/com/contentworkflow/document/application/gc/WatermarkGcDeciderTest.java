package com.contentworkflow.document.application.gc;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class WatermarkGcDeciderTest {

    @Test
    void shouldReclaimSegment_shouldReturnTrueWhenUpperClockIsBelowWatermark() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(20L));

        boolean reclaimable = decider.shouldReclaimSegment(100L, 18L);
        WatermarkGcDecider.GcDecision decision = decider.decide(100L, 18L);

        assertThat(reclaimable).isTrue();
        assertThat(decision.reclaimable()).isTrue();
        assertThat(decision.reason()).isEqualTo("below_or_equal_minimum_online_clock");
        assertThat(decision.minimumOnlineClock()).isEqualTo(20L);
    }

    @Test
    void shouldReclaimSegment_shouldReturnFalseWhenWatermarkUnavailable() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.empty());

        boolean reclaimable = decider.shouldReclaimSegment(100L, 18L);
        WatermarkGcDecider.GcDecision decision = decider.decide(100L, 18L);

        assertThat(reclaimable).isFalse();
        assertThat(decision.reclaimable()).isFalse();
        assertThat(decision.reason()).isEqualTo("watermark_unavailable");
        assertThat(decision.minimumOnlineClock()).isNull();
    }

    @Test
    void decide_shouldReturnFalseWhenDocumentIdInvalid() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(10L));

        WatermarkGcDecider.GcDecision decision = decider.decide(0L, 10L);

        assertThat(decision.reclaimable()).isFalse();
        assertThat(decision.reason()).isEqualTo("invalid_document_id");
        assertThat(decision.minimumOnlineClock()).isNull();
    }

    @Test
    void decide_shouldReturnFalseWhenUpperClockInvalid() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(10L));

        WatermarkGcDecider.GcDecision decision = decider.decide(100L, 0L);

        assertThat(decision.reclaimable()).isFalse();
        assertThat(decision.reason()).isEqualTo("invalid_segment_upper_clock");
        assertThat(decision.minimumOnlineClock()).isNull();
    }

    @Test
    void decide_shouldReturnFalseWhenWatermarkValueInvalid() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(-1L));

        WatermarkGcDecider.GcDecision decision = decider.decide(100L, 10L);

        assertThat(decision.reclaimable()).isFalse();
        assertThat(decision.reason()).isEqualTo("invalid_watermark_value");
        assertThat(decision.minimumOnlineClock()).isEqualTo(-1L);
    }

    @Test
    void decide_shouldReturnFalseWhenProviderThrows() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> {
            throw new RuntimeException("boom");
        });

        WatermarkGcDecider.GcDecision decision = decider.decide(100L, 10L);

        assertThat(decision.reclaimable()).isFalse();
        assertThat(decision.reason()).isEqualTo("watermark_provider_error");
        assertThat(decision.minimumOnlineClock()).isNull();
    }

    @Test
    void decide_shouldReclaimWhenUpperClockEqualsWatermark() {
        WatermarkGcDecider decider = new WatermarkGcDecider(documentId -> OptionalLong.of(10L));

        WatermarkGcDecider.GcDecision decision = decider.decide(100L, 10L);

        assertThat(decision.reclaimable()).isTrue();
        assertThat(decision.reason()).isEqualTo("below_or_equal_minimum_online_clock");
        assertThat(decision.minimumOnlineClock()).isEqualTo(10L);
    }
}

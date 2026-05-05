package com.contentworkflow.document.application.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

@Component
public class WatermarkGcDecider {

    private static final Logger log = LoggerFactory.getLogger(WatermarkGcDecider.class);

    private final CompactionWatermarkProvider watermarkProvider;

    public WatermarkGcDecider(CompactionWatermarkProvider watermarkProvider) {
        this.watermarkProvider = watermarkProvider;
    }

    public GcDecision decide(Long documentId, long segmentUpperClockInclusive) {
        if (documentId == null || documentId <= 0L) {
            return new GcDecision(false, "invalid_document_id", null);
        }
        if (segmentUpperClockInclusive <= 0L) {
            return new GcDecision(false, "invalid_segment_upper_clock", null);
        }

        OptionalLong watermark;
        try {
            watermark = watermarkProvider.minimumOnlineClock(documentId);
        } catch (Exception ex) {
            log.warn("load watermark failed, docId={}, upperClock={}", documentId, segmentUpperClockInclusive, ex);
            return new GcDecision(false, "watermark_provider_error", null);
        }
        if (watermark.isEmpty()) {
            return new GcDecision(false, "watermark_unavailable", null);
        }
        long minimumOnlineClock = watermark.getAsLong();
        if (minimumOnlineClock < 0L) {
            log.warn("invalid watermark value, docId={}, minimumOnlineClock={}", documentId, minimumOnlineClock);
            return new GcDecision(false, "invalid_watermark_value", minimumOnlineClock);
        }
        if (segmentUpperClockInclusive <= minimumOnlineClock) {
            return new GcDecision(true, "below_or_equal_minimum_online_clock", minimumOnlineClock);
        }
        return new GcDecision(false, "active_user_may_need_segment", minimumOnlineClock);
    }

    public boolean shouldReclaimSegment(Long documentId, long segmentUpperClockInclusive) {
        return decide(documentId, segmentUpperClockInclusive).reclaimable();
    }

    public record GcDecision(boolean reclaimable, String reason, Long minimumOnlineClock) {
    }
}

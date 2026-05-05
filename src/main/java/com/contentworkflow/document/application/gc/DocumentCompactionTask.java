package com.contentworkflow.document.application.gc;

import java.time.Instant;

public record DocumentCompactionTask(
        Long documentId,
        String trigger,
        Instant createdAt,
        Long segmentUpperClockInclusive
) {
    private static final String LEGACY_UPPER_CLOCK_KEY = "upperClock=";

    public DocumentCompactionTask(Long documentId, String trigger, Instant createdAt) {
        this(documentId, trigger, createdAt, null);
    }

    public DocumentCompactionTask {
        segmentUpperClockInclusive = resolveUpperClock(trigger, segmentUpperClockInclusive);
    }

    private static Long resolveUpperClock(String trigger, Long segmentUpperClockInclusive) {
        if (segmentUpperClockInclusive != null && segmentUpperClockInclusive > 0L) {
            return segmentUpperClockInclusive;
        }
        if (trigger == null || trigger.isBlank()) {
            return null;
        }
        int index = trigger.indexOf(LEGACY_UPPER_CLOCK_KEY);
        if (index < 0) {
            return null;
        }
        String value = trigger.substring(index + LEGACY_UPPER_CLOCK_KEY.length()).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0L ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

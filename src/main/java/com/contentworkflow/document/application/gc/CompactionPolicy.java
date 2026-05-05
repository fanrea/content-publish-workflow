package com.contentworkflow.document.application.gc;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Policy for deciding whether compaction should be triggered.
 */
public class CompactionPolicy {

    private final long updateCountThreshold;
    private final double growthRatioThreshold;
    private final Duration timeWindow;

    public CompactionPolicy(long updateCountThreshold, double growthRatioThreshold, Duration timeWindow) {
        if (updateCountThreshold <= 0) {
            throw new IllegalArgumentException("updateCountThreshold must be > 0");
        }
        if (growthRatioThreshold <= 1.0d) {
            throw new IllegalArgumentException("growthRatioThreshold must be > 1.0");
        }
        this.updateCountThreshold = updateCountThreshold;
        this.growthRatioThreshold = growthRatioThreshold;
        this.timeWindow = Objects.requireNonNull(timeWindow, "timeWindow must not be null");
    }

    public Decision evaluate(CompactionStats stats, Instant now) {
        Objects.requireNonNull(stats, "stats must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (stats.updatesSinceLastCompaction() >= updateCountThreshold) {
            return new Decision(true, "update_count_threshold");
        }
        if (isGrowthTriggered(stats)) {
            return new Decision(true, "growth_ratio_threshold");
        }
        if (isTimeWindowTriggered(stats, now)) {
            return new Decision(true, "time_window_threshold");
        }
        return new Decision(false, "not_reached");
    }

    private boolean isGrowthTriggered(CompactionStats stats) {
        if (stats.baselineSnapshotBytes() <= 0L || stats.latestSnapshotBytes() <= 0L) {
            return false;
        }
        double ratio = (double) stats.latestSnapshotBytes() / (double) stats.baselineSnapshotBytes();
        return ratio >= growthRatioThreshold;
    }

    private boolean isTimeWindowTriggered(CompactionStats stats, Instant now) {
        Instant lastCompactionAt = stats.lastCompactionAt();
        if (lastCompactionAt == null) {
            return false;
        }
        if (timeWindow.isZero() || timeWindow.isNegative()) {
            return false;
        }
        Duration elapsed = Duration.between(lastCompactionAt, now);
        return !elapsed.isNegative() && elapsed.compareTo(timeWindow) >= 0;
    }

    public record CompactionStats(
            long updatesSinceLastCompaction,
            long latestSnapshotBytes,
            long baselineSnapshotBytes,
            Instant lastCompactionAt
    ) {
    }

    public record Decision(boolean shouldCompact, String reason) {
    }
}


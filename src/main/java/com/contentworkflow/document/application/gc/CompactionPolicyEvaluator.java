package com.contentworkflow.document.application.gc;

import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Evaluates compaction triggers and maintains lightweight in-memory per-document stats.
 */
@Component
public class CompactionPolicyEvaluator {

    private static final long MIN_ESTIMATED_BYTES = 1L;

    private final int updateCountThreshold;
    private final double growthRatioThreshold;
    private final Duration timeWindow;
    private final Duration publishMinInterval;
    private final Clock clock;
    private final ConcurrentMap<Long, DocumentCompactionStats> statsByDocument = new ConcurrentHashMap<>();

    @Autowired
    public CompactionPolicyEvaluator(
            @Value("${workflow.gc.compaction.update-count-threshold:200}") int updateCountThreshold,
            @Value("${workflow.gc.compaction.growth-ratio-threshold:1.5}") double growthRatioThreshold,
            @Value("${workflow.gc.compaction.time-window:10m}") Duration timeWindow,
            @Value("${workflow.gc.compaction.publish-min-interval:30s}") Duration publishMinInterval) {
        this(updateCountThreshold, growthRatioThreshold, timeWindow, publishMinInterval, Clock.systemUTC());
    }

    CompactionPolicyEvaluator(int updateCountThreshold,
                              double growthRatioThreshold,
                              Duration timeWindow,
                              Duration publishMinInterval,
                              Clock clock) {
        if (updateCountThreshold <= 0) {
            throw new IllegalArgumentException("updateCountThreshold must be > 0");
        }
        if (growthRatioThreshold <= 1.0d) {
            throw new IllegalArgumentException("growthRatioThreshold must be > 1.0");
        }
        this.updateCountThreshold = updateCountThreshold;
        this.growthRatioThreshold = growthRatioThreshold;
        this.timeWindow = timeWindow == null ? Duration.ZERO : timeWindow;
        this.publishMinInterval = publishMinInterval == null ? Duration.ZERO : publishMinInterval;
        this.clock = clock;
    }

    public CompactionPolicyDecision evaluate(CompactionPolicyInput input, Instant now) {
        EnumSet<CompactionTrigger> triggers = EnumSet.noneOf(CompactionTrigger.class);
        if (input.updatesSinceLastCompaction() >= updateCountThreshold) {
            triggers.add(CompactionTrigger.UPDATE_COUNT);
        }
        if (isGrowthRatioTriggered(input)) {
            triggers.add(CompactionTrigger.GROWTH_RATIO);
        }
        if (isTimeWindowTriggered(input, now)) {
            triggers.add(CompactionTrigger.TIME_WINDOW);
        }
        if (triggers.isEmpty()) {
            return CompactionPolicyDecision.noCompact();
        }
        return new CompactionPolicyDecision(true, triggers);
    }

    public Optional<DocumentCompactionTask> onOperationApplied(DocumentOperation operation) {
        if (operation == null || operation.getDocumentId() == null) {
            return Optional.empty();
        }
        Long documentId = operation.getDocumentId();
        Instant now = Instant.now(clock);
        DocumentCompactionStats stats = statsByDocument.computeIfAbsent(
                documentId,
                ignored -> new DocumentCompactionStats(MIN_ESTIMATED_BYTES, MIN_ESTIMATED_BYTES, now)
        );

        synchronized (stats) {
            if (isDuplicateOrOutdated(operation, stats)) {
                return Optional.empty();
            }
            long nextBytes = estimateSnapshotBytesAfterOperation(stats.currentSnapshotBytes, operation);
            stats.currentSnapshotBytes = nextBytes;
            stats.updatesSinceLastCompaction++;
            stats.lastSeenRevision = operation.getRevisionNo();

            CompactionPolicyInput input = new CompactionPolicyInput(
                    stats.updatesSinceLastCompaction,
                    stats.compactedSnapshotBytes,
                    stats.currentSnapshotBytes,
                    stats.lastCompactionAt
            );
            CompactionPolicyDecision decision = evaluate(input, now);
            if (!decision.shouldCompact()) {
                return Optional.empty();
            }
            if (shouldThrottle(stats, now)) {
                return Optional.empty();
            }

            stats.lastPublishedAt = now;
            stats.lastPublishedRevision = operation.getRevisionNo();
            stats.updatesSinceLastCompaction = 0;
            stats.compactedSnapshotBytes = Math.max(MIN_ESTIMATED_BYTES, stats.currentSnapshotBytes);
            stats.lastCompactionAt = now;

            return Optional.of(new DocumentCompactionTask(
                    documentId,
                    triggerName(decision),
                    now
            ));
        }
    }

    private boolean isGrowthRatioTriggered(CompactionPolicyInput input) {
        if (input.compactedSnapshotBytes() <= 0L || input.currentSnapshotBytes() <= 0L) {
            return false;
        }
        double ratio = (double) input.currentSnapshotBytes() / (double) input.compactedSnapshotBytes();
        return ratio >= growthRatioThreshold;
    }

    private boolean isTimeWindowTriggered(CompactionPolicyInput input, Instant now) {
        if (timeWindow.isNegative() || timeWindow.isZero() || input.lastCompactionAt() == null) {
            return false;
        }
        Duration elapsed = Duration.between(input.lastCompactionAt(), now);
        return !elapsed.isNegative() && elapsed.compareTo(timeWindow) >= 0;
    }

    private boolean isDuplicateOrOutdated(DocumentOperation operation, DocumentCompactionStats stats) {
        Integer revisionNo = operation.getRevisionNo();
        Integer lastSeenRevision = stats.lastSeenRevision;
        if (revisionNo == null || lastSeenRevision == null) {
            return false;
        }
        return revisionNo <= lastSeenRevision;
    }

    private boolean shouldThrottle(DocumentCompactionStats stats, Instant now) {
        if (publishMinInterval.isNegative() || publishMinInterval.isZero() || stats.lastPublishedAt == null) {
            return false;
        }
        Duration sinceLastPublish = Duration.between(stats.lastPublishedAt, now);
        return !sinceLastPublish.isNegative() && sinceLastPublish.compareTo(publishMinInterval) < 0;
    }

    private long estimateSnapshotBytesAfterOperation(long currentBytes, DocumentOperation operation) {
        long resolvedCurrent = Math.max(MIN_ESTIMATED_BYTES, currentBytes);
        DocumentOpType opType = operation.getOpType();
        int opLength = operation.getOpLength() == null ? 0 : Math.max(0, operation.getOpLength());
        int textLength = operation.getOpText() == null ? 0 : operation.getOpText().length();
        if (opType == null) {
            return resolvedCurrent;
        }
        long next;
        switch (opType) {
            case INSERT -> next = resolvedCurrent + textLength;
            case DELETE -> next = resolvedCurrent - opLength;
            case REPLACE -> next = resolvedCurrent - opLength + textLength;
            default -> next = resolvedCurrent;
        }
        return Math.max(MIN_ESTIMATED_BYTES, next);
    }

    private String triggerName(CompactionPolicyDecision decision) {
        if (decision.triggers().contains(CompactionTrigger.UPDATE_COUNT)) {
            return "UPDATE_COUNT";
        }
        if (decision.triggers().contains(CompactionTrigger.GROWTH_RATIO)) {
            return "GROWTH_RATIO";
        }
        return "TIME_WINDOW";
    }

    private static final class DocumentCompactionStats {
        private int updatesSinceLastCompaction;
        private long compactedSnapshotBytes;
        private long currentSnapshotBytes;
        private Instant lastCompactionAt;
        private Instant lastPublishedAt;
        private Integer lastPublishedRevision;
        private Integer lastSeenRevision;

        private DocumentCompactionStats(long compactedSnapshotBytes,
                                        long currentSnapshotBytes,
                                        Instant lastCompactionAt) {
            this.compactedSnapshotBytes = compactedSnapshotBytes;
            this.currentSnapshotBytes = currentSnapshotBytes;
            this.lastCompactionAt = lastCompactionAt;
        }
    }
}

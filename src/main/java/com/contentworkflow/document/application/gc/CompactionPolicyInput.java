package com.contentworkflow.document.application.gc;

import java.time.Instant;

/**
 * Signals used to evaluate whether snapshot compaction should be triggered.
 */
public record CompactionPolicyInput(
        int updatesSinceLastCompaction,
        long compactedSnapshotBytes,
        long currentSnapshotBytes,
        Instant lastCompactionAt
) {
    public CompactionPolicyInput {
        if (updatesSinceLastCompaction < 0) {
            throw new IllegalArgumentException("updatesSinceLastCompaction must be >= 0");
        }
        if (compactedSnapshotBytes < 0) {
            throw new IllegalArgumentException("compactedSnapshotBytes must be >= 0");
        }
        if (currentSnapshotBytes < 0) {
            throw new IllegalArgumentException("currentSnapshotBytes must be >= 0");
        }
    }
}

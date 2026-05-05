package com.contentworkflow.document.application.gc;

import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionPolicyEvaluatorTest {

    @Test
    void onOperationApplied_shouldTriggerByUpdateCountThreshold() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CompactionPolicyEvaluator evaluator = new CompactionPolicyEvaluator(
                2,
                10.0d,
                Duration.ofHours(1),
                Duration.ZERO,
                clock
        );

        Optional<DocumentCompactionTask> first = evaluator.onOperationApplied(insert(100L, 1, "a"));
        Optional<DocumentCompactionTask> second = evaluator.onOperationApplied(insert(100L, 2, "b"));

        assertThat(first).isEmpty();
        assertThat(second).isPresent();
        assertThat(second.get().trigger()).isEqualTo("UPDATE_COUNT");
    }

    @Test
    void onOperationApplied_shouldNotTriggerWhenThresholdsNotReached() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CompactionPolicyEvaluator evaluator = new CompactionPolicyEvaluator(
                5,
                10.0d,
                Duration.ofHours(1),
                Duration.ZERO,
                clock
        );

        Optional<DocumentCompactionTask> task = evaluator.onOperationApplied(insert(100L, 1, "a"));

        assertThat(task).isEmpty();
    }

    @Test
    void onOperationApplied_shouldTriggerByGrowthRatio() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CompactionPolicyEvaluator evaluator = new CompactionPolicyEvaluator(
                100,
                1.2d,
                Duration.ofHours(1),
                Duration.ZERO,
                clock
        );

        Optional<DocumentCompactionTask> task = evaluator.onOperationApplied(insert(100L, 1, "abcdef"));

        assertThat(task).isPresent();
        assertThat(task.get().trigger()).isEqualTo("GROWTH_RATIO");
    }

    @Test
    void onOperationApplied_shouldTriggerByTimeWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CompactionPolicyEvaluator evaluator = new CompactionPolicyEvaluator(
                100,
                10.0d,
                Duration.ofSeconds(5),
                Duration.ZERO,
                clock
        );

        assertThat(evaluator.onOperationApplied(insert(100L, 1, "a"))).isEmpty();
        clock.advance(Duration.ofSeconds(6));

        Optional<DocumentCompactionTask> task = evaluator.onOperationApplied(insert(100L, 2, "b"));

        assertThat(task).isPresent();
        assertThat(task.get().trigger()).isEqualTo("TIME_WINDOW");
    }

    @Test
    void onOperationApplied_shouldThrottleAndDedupePublish() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CompactionPolicyEvaluator evaluator = new CompactionPolicyEvaluator(
                1,
                1.1d,
                Duration.ZERO,
                Duration.ofSeconds(30),
                clock
        );

        Optional<DocumentCompactionTask> first = evaluator.onOperationApplied(insert(100L, 1, "a"));
        clock.advance(Duration.ofSeconds(1));
        Optional<DocumentCompactionTask> throttled = evaluator.onOperationApplied(insert(100L, 2, "b"));
        Optional<DocumentCompactionTask> duplicate = evaluator.onOperationApplied(insert(100L, 2, "b"));
        clock.advance(Duration.ofSeconds(31));
        Optional<DocumentCompactionTask> third = evaluator.onOperationApplied(insert(100L, 3, "c"));

        assertThat(first).isPresent();
        assertThat(throttled).isEmpty();
        assertThat(duplicate).isEmpty();
        assertThat(third).isPresent();
    }

    private DocumentOperation insert(Long docId, int revision, String text) {
        return DocumentOperation.builder()
                .id((long) revision)
                .documentId(docId)
                .revisionNo(revision)
                .baseRevision(Math.max(0, revision - 1))
                .sessionId("s-1")
                .clientSeq((long) revision)
                .opType(DocumentOpType.INSERT)
                .opPosition(0)
                .opLength(0)
                .opText(text)
                .editorId("u-1")
                .editorName("alice")
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant initial) {
            this.now = initial;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }
    }
}

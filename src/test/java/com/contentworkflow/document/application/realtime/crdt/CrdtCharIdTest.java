package com.contentworkflow.document.application.realtime.crdt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrdtCharIdTest {

    @Test
    void constructor_shouldNormalizeNullActorAndNegativeClock() {
        CrdtCharId id = new CrdtCharId(null, -10L);

        assertThat(id.actorId()).isEqualTo("");
        assertThat(id.clock()).isEqualTo(0L);
    }

    @Test
    void compareTo_shouldOrderByClockThenActorId() {
        CrdtCharId lowClock = new CrdtCharId("z-actor", 1L);
        CrdtCharId highClock = new CrdtCharId("a-actor", 5L);
        CrdtCharId sameClockA = new CrdtCharId("a-actor", 3L);
        CrdtCharId sameClockB = new CrdtCharId("b-actor", 3L);

        assertThat(lowClock.compareTo(highClock)).isLessThan(0);
        assertThat(sameClockA.compareTo(sameClockB)).isLessThan(0);
    }

    @Test
    void factory_shouldHandleNullClock() {
        CrdtCharId id = CrdtCharId.of("actor-a", null);

        assertThat(id.actorId()).isEqualTo("actor-a");
        assertThat(id.clock()).isEqualTo(0L);
    }
}

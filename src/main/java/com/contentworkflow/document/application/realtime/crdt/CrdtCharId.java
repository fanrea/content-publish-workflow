package com.contentworkflow.document.application.realtime.crdt;

import java.util.Objects;

public record CrdtCharId(String actorId, long clock) implements Comparable<CrdtCharId> {

    public CrdtCharId {
        actorId = actorId == null ? "" : actorId;
        clock = Math.max(0L, clock);
    }

    public static CrdtCharId of(String actorId, Long clock) {
        return new CrdtCharId(actorId, clock == null ? 0L : clock);
    }

    @Override
    public int compareTo(CrdtCharId other) {
        Objects.requireNonNull(other, "other must not be null");
        int clockCompare = Long.compare(clock, other.clock);
        if (clockCompare != 0) {
            return clockCompare;
        }
        return actorId.compareTo(other.actorId);
    }
}

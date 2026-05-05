package com.contentworkflow.document.application.realtime.crdt;

import java.util.Objects;

public class CrdtAtom {

    private final CrdtCharId id;
    private final char value;
    private boolean tombstone;

    public CrdtAtom(CrdtCharId id, char value, boolean tombstone) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.value = value;
        this.tombstone = tombstone;
    }

    public CrdtCharId getId() {
        return id;
    }

    public char getValue() {
        return value;
    }

    public boolean isTombstone() {
        return tombstone;
    }

    public void markTombstone() {
        this.tombstone = true;
    }
}

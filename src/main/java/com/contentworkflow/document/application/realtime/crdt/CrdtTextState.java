package com.contentworkflow.document.application.realtime.crdt;

import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CrdtTextState {

    private static final String SEED_ACTOR = "__seed__";

    private final List<CrdtAtom> atoms = new ArrayList<>();
    private final Map<String, Long> maxClockByActor = new HashMap<>();

    public static CrdtTextState fromPlainText(String content) {
        CrdtTextState state = new CrdtTextState();
        String source = content == null ? "" : content;
        for (int i = 0; i < source.length(); i++) {
            long clock = i + 1L;
            state.atoms.add(new CrdtAtom(new CrdtCharId(SEED_ACTOR, clock), source.charAt(i), false));
            state.maxClockByActor.put(SEED_ACTOR, clock);
        }
        return state;
    }

    static CrdtTextState fromSnapshot(List<CrdtAtom> atoms, Map<String, Long> maxClockByActor) {
        CrdtTextState state = new CrdtTextState();
        if (atoms != null) {
            for (CrdtAtom atom : atoms) {
                state.atoms.add(new CrdtAtom(
                        atom.getId(),
                        atom.getValue(),
                        atom.isTombstone()
                ));
            }
        }
        if (maxClockByActor != null) {
            for (Map.Entry<String, Long> entry : maxClockByActor.entrySet()) {
                String actorId = entry.getKey() == null ? "" : entry.getKey();
                long clock = entry.getValue() == null ? 0L : Math.max(0L, entry.getValue());
                state.maxClockByActor.put(actorId, clock);
            }
        }
        for (CrdtAtom atom : state.atoms) {
            String actorId = atom.getId().actorId();
            long clock = atom.getId().clock();
            Long known = state.maxClockByActor.get(actorId);
            if (known == null || clock > known) {
                state.maxClockByActor.put(actorId, clock);
            }
        }
        return state;
    }

    public void apply(DocumentWsOperation operation, String actorId, Long actorClockBase) {
        Objects.requireNonNull(operation, "operation must not be null");
        DocumentOpType opType = Objects.requireNonNull(operation.getOpType(), "operation.opType must not be null");
        int position = safeNonNegative(operation.getPosition());
        int length = safeNonNegative(operation.getLength());
        String text = operation.getText() == null ? "" : operation.getText();

        switch (opType) {
            case INSERT -> insert(position, text, actorId, actorClockBase);
            case DELETE -> tombstoneDelete(position, length);
            case REPLACE -> {
                tombstoneDelete(position, length);
                insert(position, text, actorId, actorClockBase);
            }
        }
    }

    public String exportText() {
        StringBuilder builder = new StringBuilder();
        for (CrdtAtom atom : atoms) {
            if (!atom.isTombstone()) {
                builder.append(atom.getValue());
            }
        }
        return builder.toString();
    }

    public int visibleLength() {
        int length = 0;
        for (CrdtAtom atom : atoms) {
            if (!atom.isTombstone()) {
                length++;
            }
        }
        return length;
    }

    public int clampVisiblePosition(int visiblePosition) {
        return Math.max(0, Math.min(safeNonNegative(visiblePosition), visibleLength()));
    }

    public int resolveDeleteLength(int visiblePosition, int requestedLength) {
        if (requestedLength <= 0) {
            return 0;
        }
        int start = clampVisiblePosition(visiblePosition);
        int remaining = visibleLength() - start;
        if (remaining <= 0) {
            return 0;
        }
        return Math.min(requestedLength, remaining);
    }

    public List<CrdtAtom> visibleAtoms() {
        List<CrdtAtom> visible = new ArrayList<>();
        for (CrdtAtom atom : atoms) {
            if (!atom.isTombstone()) {
                visible.add(atom);
            }
        }
        return visible;
    }

    public int atomIndexOf(CrdtCharId id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < atoms.size(); i++) {
            if (atoms.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public int visiblePositionBeforeAtomIndex(int atomIndexExclusive) {
        int to = Math.max(0, Math.min(atomIndexExclusive, atoms.size()));
        int visible = 0;
        for (int i = 0; i < to; i++) {
            if (!atoms.get(i).isTombstone()) {
                visible++;
            }
        }
        return visible;
    }

    public int compactTombstones() {
        int before = atoms.size();
        atoms.removeIf(CrdtAtom::isTombstone);
        return before - atoms.size();
    }

    public List<CrdtAtom> atoms() {
        return List.copyOf(atoms);
    }

    Map<String, Long> actorClocks() {
        return Map.copyOf(maxClockByActor);
    }

    private void insert(int visiblePosition, String text, String actorId, Long actorClockBase) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalizedActorId = actorId == null ? "" : actorId;
        int insertIndex = resolveInsertIndex(visiblePosition);
        long baseClock = actorClockBase == null ? 0L : Math.max(0L, actorClockBase);
        for (int i = 0; i < text.length(); i++) {
            long clock = nextClock(normalizedActorId, baseClock + i);
            CrdtAtom atom = new CrdtAtom(new CrdtCharId(normalizedActorId, clock), text.charAt(i), false);
            atoms.add(insertIndex, atom);
            insertIndex++;
        }
    }

    private void tombstoneDelete(int visiblePosition, int length) {
        int deleteLength = resolveDeleteLength(visiblePosition, length);
        if (deleteLength <= 0) {
            return;
        }
        int clampedStart = clampVisiblePosition(visiblePosition);
        int consumed = 0;
        int visibleIndex = 0;
        for (CrdtAtom atom : atoms) {
            if (atom.isTombstone()) {
                continue;
            }
            if (visibleIndex >= clampedStart && consumed < deleteLength) {
                atom.markTombstone();
                consumed++;
            }
            visibleIndex++;
            if (consumed >= deleteLength) {
                break;
            }
        }
    }

    private int resolveInsertIndex(int visiblePosition) {
        int clampedVisiblePosition = Math.max(0, visiblePosition);
        int visibleIndex = 0;
        for (int i = 0; i < atoms.size(); i++) {
            CrdtAtom atom = atoms.get(i);
            if (atom.isTombstone()) {
                continue;
            }
            if (visibleIndex == clampedVisiblePosition) {
                return i;
            }
            visibleIndex++;
        }
        return atoms.size();
    }

    private long nextClock(String actorId, long minimumClock) {
        long current = maxClockByActor.getOrDefault(actorId, 0L);
        long next = Math.max(current + 1, minimumClock <= 0 ? current + 1 : minimumClock);
        maxClockByActor.put(actorId, next);
        return next;
    }

    private int safeNonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}

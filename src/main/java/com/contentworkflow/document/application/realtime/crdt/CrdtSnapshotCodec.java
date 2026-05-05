package com.contentworkflow.document.application.realtime.crdt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class CrdtSnapshotCodec {

    private static final String VERSION_PREFIX = "crdt-v1:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String encodeText(String text) {
        return encode(CrdtTextState.fromPlainText(text));
    }

    public String encode(CrdtTextState state) {
        Objects.requireNonNull(state, "state must not be null");
        SnapshotPayload payload = new SnapshotPayload();
        payload.atoms = new ArrayList<>();
        for (CrdtAtom atom : state.atoms()) {
            AtomPayload atomPayload = new AtomPayload();
            atomPayload.actorId = atom.getId().actorId();
            atomPayload.clock = atom.getId().clock();
            atomPayload.value = String.valueOf(atom.getValue());
            atomPayload.tombstone = atom.isTombstone();
            payload.atoms.add(atomPayload);
        }
        payload.actorClocks = new TreeMap<>(state.actorClocks());
        try {
            return VERSION_PREFIX + OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to encode crdt snapshot", exception);
        }
    }

    public CrdtTextState decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("crdt snapshot payload must not be blank");
        }
        if (!payload.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("unsupported crdt snapshot version prefix");
        }
        String json = payload.substring(VERSION_PREFIX.length());
        try {
            SnapshotPayload decoded = OBJECT_MAPPER.readValue(json, SnapshotPayload.class);
            return toState(decoded);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid crdt snapshot payload", exception);
        }
    }

    public String decodeToText(String payload) {
        return decode(payload).exportText();
    }

    public boolean isEncodedPayload(String payload) {
        return payload != null && payload.startsWith(VERSION_PREFIX);
    }

    private CrdtTextState toState(SnapshotPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("invalid crdt snapshot payload: empty json object");
        }
        List<CrdtAtom> atoms = new ArrayList<>();
        Set<CrdtCharId> seenIds = new HashSet<>();
        List<AtomPayload> decodedAtoms = payload.atoms == null ? List.of() : payload.atoms;
        for (AtomPayload atomPayload : decodedAtoms) {
            String value = atomPayload == null ? null : atomPayload.value;
            if (value == null || value.length() != 1) {
                throw new IllegalArgumentException("crdt snapshot atom value must contain exactly one character");
            }
            String actorId = atomPayload.actorId == null ? "" : atomPayload.actorId;
            long clock = atomPayload.clock == null ? 0L : atomPayload.clock;
            boolean tombstone = atomPayload.tombstone != null && atomPayload.tombstone;
            CrdtCharId id = new CrdtCharId(actorId, clock);
            if (!seenIds.add(id)) {
                throw new IllegalArgumentException("duplicate crdt atom id in snapshot payload");
            }
            atoms.add(new CrdtAtom(id, value.charAt(0), tombstone));
        }
        Map<String, Long> actorClocks = new HashMap<>();
        if (payload.actorClocks != null) {
            for (Map.Entry<String, Long> entry : payload.actorClocks.entrySet()) {
                String actorId = entry.getKey() == null ? "" : entry.getKey();
                long clock = entry.getValue() == null ? 0L : Math.max(0L, entry.getValue());
                Long previous = actorClocks.get(actorId);
                if (previous == null || clock > previous) {
                    actorClocks.put(actorId, clock);
                }
            }
        }
        return CrdtTextState.fromSnapshot(atoms, actorClocks);
    }

    static final class SnapshotPayload {
        public List<AtomPayload> atoms;
        public Map<String, Long> actorClocks;
    }

    static final class AtomPayload {
        public String actorId;
        public Long clock;
        public String value;
        public Boolean tombstone;
    }
}

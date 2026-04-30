# CRDT + Storage Migration Status

Last updated: 2026-04-30

## 1) Stage boundary (for audit/interview)

### Stage 1 (already landed)
- Unified write pipeline is runnable for HTTP + WS edit paths.
- Ingress-to-actor single-thread scheduling is runnable (`docId` shard -> serial apply).
- Idempotency key (`documentId + sessionId + clientSeq`) is in place.
- Merge engine default is now `crdt` (configurable back to `ot` for compatibility).

### Stage 2 (already landed, current closure baseline)
- JOIN / SYNC / broadcast loop is runnable.
- Gateway strict-stateless mode is available and defaulted for JOIN/SYNC decision path.
- Redis room/session indexing, recent-update cache, and cross-gateway broadcast integration are in place behind flags.
- Actor single-writer persistence switch is in place (`workflow.realtime.actor-single-writer.enabled`):
  - `true` (default) keeps actor/queue order as write authority and uses `latest_revision` as DB durability guard.
  - `false` is compatibility mode that falls back to optimistic lock update.

## 2) What is completed now (including this round)

1. Snapshot/object storage is now the primary snapshot body path:
   - Snapshot store abstraction + filesystem backend is the default.
   - Snapshot payload is CRDT codec payload (`crdt-v1`) by default.
   - Revision reconstruction reads from snapshot store first, with plain-text payload compatibility fallback.

2. Delta-store abstraction is active with non-MySQL default:
   - Default backend is `filesystem`.
   - `mysql` backend is retained as compatibility fallback only (non-default, non-recommended).

3. Revision table usage is narrowed:
   - `collaborative_document_revision` keeps metadata catalog (`revisionNo/baseRevision/title/changeType/...`).
   - Snapshot/fulltext body no longer needs to be written into revision rows for new writes.

4. Realtime gateway loop is explicit:
   - JOIN/SYNC decisions centralized through gateway facade.
   - SYNC reconnect can re-bind room/presence and continue receiving broadcast.

5. Cross-gateway delivery baseline exists:
   - Local push + redis bus forwarding + room session registry are connected.

6. Actor single-writer persistence switch is explicit:
   - Mapper + SQL + service branch support non-`lock_version` guarded actor update path.
   - `where latest_revision = expectedRevision` is retained as idempotent durability gate, not sequencing authority.

7. Config surface is explicit in `application.yml`:
   - `workflow.realtime.actor-single-writer.enabled`
   - `workflow.gc.compaction.*` policy parameters

## 3) What is still NOT completed

1. Real CRDT state model is not implemented as runtime truth:
   - No production CRDT state container as authoritative in-memory model.
   - CRDT merge engine is default, but still text-operation based integration (not full binary CRDT op log end-to-end).

2. Client-side CRDT runtime is not implemented:
   - No Worker/WASM-backed CRDT client execution layer in this repo scope.

3. Real compaction executor is not implemented:
   - Current GC compaction settings are policy/signal-level only.
   - No full snapshot+delta compaction worker that rewrites/cleans history at scale.

4. Watermark/tombstone retention is minimally landed:
   - A runnable watermark-based tombstone GC scheduling loop now exists (docId + segmentUpperClock -> decider -> publish task).
   - Production-grade segmented reclaim executor and durable progress tracking are still not implemented.

5. Full DB write-path de-emphasis is not completed:
   - `collaborative_document.content` still carries current read-model text.
   - Metadata and compatibility paths still rely on relational persistence.

## 4) Current runnable closure (what can be demonstrated now)

The repository can demonstrate a working closure for:
- Create/Edit/Replay loop with actor-based sequencing.
- WS JOIN/SYNC/broadcast with strict-stateless-capable gateway behavior.
- Cross-gateway message fanout and presence routing baseline (feature-flag controlled).
- Actor-single-writer mode as default persistence profile with revision-guard update.

This is a valid Stage1/Stage2 closure, but not yet a full CRDT-native Stage3/Stage4 architecture.

## 5) Configuration reference used by current code defaults

- `workflow.storage.snapshot.backend` -> default `filesystem`
- `workflow.operation-log.backend` -> default `filesystem` (`mysql` compatibility fallback)
- `workflow.realtime.merge-engine` -> default `crdt` (`ot` compatibility fallback)
- `workflow.realtime.actor-single-writer.enabled` -> default `true`
- `workflow.gc.compaction.update-count-threshold` -> default `200`
- `workflow.gc.compaction.growth-ratio-threshold` -> default `1.5`
- `workflow.gc.compaction.time-window` -> default `10m`
- `workflow.gc.compaction.publish-min-interval` -> default `30s`

All above defaults are aligned between code `@Value(...:default)` and `application.yml`.

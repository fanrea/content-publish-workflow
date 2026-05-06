# CRDT-Style Storage Migration Status

Last updated: 2026-05-06

## Scope Statement

This repository is positioned as:

- Server-ordered operation processing (`docId` shard, monotonic `revision`)
- Server-side position projection with CRDT-style character-id relocation
- Operation-log/revision based replay and recovery

It is **not** positioned as a full client-side CRDT runtime.

## What Is Already Landed

1. Unified write ingress for HTTP and WebSocket edit paths.
2. RocketMQ ordered ingress path is runnable and is the primary consistency entrance.
3. Merge/apply is executed on server actor flow, then persisted with revision metadata and operation log.
4. JOIN/SYNC/broadcast loop is runnable with reconnect replay.
5. Snapshot + operation-log adapters are available for local/dev and compatibility.
6. Actor single-writer persistence mode is available and defaulted.

## Storage and Consistency Baseline

Global consistency is provided by:

1. RocketMQ ordered ingress
2. Server merge engine
3. Revision + operation log replay

Client replicas do not converge independently and are not the source of global truth.

## Metadata Clarification (`clientClock` / `baseVector`)

`clientClock` and `baseVector` are optional metadata for:

- server-side ordering assistance
- presence watermark hints
- replay/compensation diagnostics

They are **not** client-side CRDT convergence evidence and are not used as independent convergence authority.

## Not Completed Yet

1. No full end-side CRDT runtime (worker/wasm/native CRDT op graph on clients).
2. No CRDT-op-native storage as end-to-end canonical format.
3. No production-grade compaction executor that rewrites long history segments at scale.


# Collaborative Architecture

## 1. Positioning

This repository is a server-authoritative collaborative editing backend:

- ordered ingest and apply on server
- revision-driven replay and recovery
- CRDT-style character-id relocation for position projection and anchor relocation

It is not a full end-side CRDT mesh.

## 2. Write Path

1. Client sends `EDIT_OP` with `baseRevision`.
2. Ingress publishes to RocketMQ ordered stream.
3. Server merge engine applies in ordered actor flow.
4. Persistence writes document state + revision metadata + operation log.
5. `OP_APPLIED` is broadcast after commit.

## 3. Read/Recovery Path

1. `JOIN` returns snapshot context.
2. Reconnect or gap uses `SYNC_OPS` from last applied revision.
3. Replay source order: recent cache (if present) then durable operation log.

## 4. Consistency Contract

Global consistency is guaranteed by:

- RocketMQ ordered ingress
- server merge engine
- operation log and revision replay

Clients are consumers of server order; they do not independently establish convergence.

## 5. Metadata Contract

`clientClock` and `baseVector` are optional metadata only:

- server ordering/watermark assistance
- presence progress hints
- replay/compensation diagnostics

They are not client-side CRDT convergence vectors and are not authoritative for merge outcomes.

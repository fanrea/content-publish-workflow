# Collaborative Document Service

Backend collaborative editing service based on Spring Boot, MyBatis-Plus, MySQL, Redis, RocketMQ, and WebSocket.

## Unified Model

This repository uses:

- server-ordered operation processing
- server-side merge engine
- server-side position projection and comment-anchor relocation with CRDT-style character-id mapping

This is **not** a full client-side CRDT runtime.

## Consistency Source

Global consistency is established by:

1. RocketMQ ordered ingress
2. server merge/apply
3. operation log + revision replay

Client does not independently converge without server sequencing.

## Protocol Notes

- `ACK(acceptedAck)` means ingress accepted the request.
- `OP_APPLIED` means operation is ordered, merged, and globally effective.
- stale `baseRevision` may trigger `REBASE_REQUIRED` or `SNAPSHOT_REQUIRED`.

## Metadata Notes

`clientClock` and `baseVector` are optional metadata for:

- server ordering/presence watermark hints
- replay and compensation diagnostics

They are not client-side convergence authority.

## Docs

- [docs/architecture-collab.md](docs/architecture-collab.md)
- [docs/SERVER_ORDERED_MERGE.md](docs/SERVER_ORDERED_MERGE.md)
- [docs/STAGE2_REALTIME_LOCAL.md](docs/STAGE2_REALTIME_LOCAL.md)
- [docs/INGRESS_ROCKETMQ_LOCAL.md](docs/INGRESS_ROCKETMQ_LOCAL.md)
- [docs/CRDT_STORAGE_MIGRATION_STATUS.md](docs/CRDT_STORAGE_MIGRATION_STATUS.md)


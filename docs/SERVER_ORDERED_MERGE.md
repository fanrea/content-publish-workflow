# Server-Ordered Merge Semantics

## 1. Canonical Model

The collaborative model in this project is:

- server-ordered operation log (`revision` as the global sequence)
- server-side merge/apply
- server-side position projection and comment-anchor relocation with CRDT-style character-id mapping

`merge-engine=crdt` means a CRDT-style relocation strategy on the server.  
It does **not** mean full client-side CRDT convergence.

## 2. Operation Contract

Supported operations:

- `INSERT(position, text)`
- `DELETE(position, length)`
- `REPLACE(position, length, text)` (atomic `DELETE + INSERT`)

All operations are accepted with `baseRevision`, then projected to latest server order before persistence.

## 3. Ack and Applied Are Different

- `ACK(acceptedAck)` means ingress accepted the request.
- `OP_APPLIED` means server ordering and merge are complete and effective globally.

## 4. JOIN / SYNC State Machine

`INIT -> JOINING -> SNAPSHOT_READY -> EDITING -> GAP_DETECTED -> SYNCING -> EDITING`

- `JOIN` returns snapshot + latest revision context.
- `SYNC_OPS` replays increments after `baseRevision`.
- stale `baseRevision` leads to `REBASE_REQUIRED` or `SNAPSHOT_REQUIRED`.

## 5. Global Consistency Source

Global consistency depends on:

1. RocketMQ ordered ingress
2. server merge engine
3. operation log + revision replay

Client does not independently converge without server sequencing.

## 6. Metadata Scope (`clientClock` / `baseVector`)

These fields are optional metadata, used for:

- server ordering hints
- presence watermark progression
- compensation/replay diagnostics

They are not the primary convergence basis and do not replace server revision ordering.


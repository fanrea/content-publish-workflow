# Stage2 Realtime Local Validation

This guide validates the local collaborative loop:

`JOIN -> EDIT_OP -> OP_APPLIED -> SYNC_OPS`

## 1. Semantics to Validate

1. `ACK(acceptedAck)` only means ingress accepted.
2. `OP_APPLIED` means server ordering/merge is complete.
3. Consistency comes from RocketMQ ordered ingress + server merge + revision/op-log replay.
4. Client does not independently converge.

## 2. Dependencies

```bash
docker compose -f compose.local.yml up -d mysql redis
docker compose -f compose.local.yml --profile rocketmq up -d rocketmq-namesrv rocketmq-broker
```

## 3. Run Application

```powershell
$env:DOC_INGRESS_ROCKETMQ_ENABLED="true"
$env:INGRESS_ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
$env:DOC_REALTIME_REDIS_INDEX_ENABLED="true"
$env:DOC_REALTIME_GATEWAY_ID="gateway-local-1"
$env:DOC_REALTIME_RECENT_UPDATES_ENABLED="true"
$env:DOC_REALTIME_RECENT_UPDATES_SIZE="200"
$env:DOC_REALTIME_RECENT_UPDATES_TTL="120s"
mvn spring-boot:run
```

## 4. Validation Steps

1. Create document by HTTP and record `docId/latestRevision`.
2. Send `JOIN`; verify snapshot context is returned.
3. Send `EDIT_OP`; verify `ACK` first, then `OP_APPLIED`.
4. Send `SYNC_OPS` from lagging revision; verify replay and `SYNC_DONE`.

## 5. Metadata Check (`clientClock` / `baseVector`)

When provided, they should be treated as optional metadata for:

- server ordering/watermark hints
- presence progress
- compensation diagnostics

They must not be treated as independent client-side convergence authority.


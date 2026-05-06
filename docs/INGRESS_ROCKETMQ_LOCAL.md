# Ingress RocketMQ Local Guide

This guide covers local debugging for `workflow.ingress.rocketmq.*` on the collaborative write path.

## 1. Primary Path Statement

Primary consistency path:

1. RocketMQ ordered ingress
2. server merge/apply
3. revision + operation-log replay

`ACK` means ingress accepted; `OP_APPLIED` means globally effective in server order.

## 2. Dependencies

```bash
docker compose -f compose.local.yml up -d mysql redis
docker compose -f compose.local.yml --profile rocketmq up -d rocketmq-namesrv rocketmq-broker
```

## 3. Run Application

```powershell
$env:DOC_INGRESS_ROCKETMQ_ENABLED="true"
$env:INGRESS_ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
mvn spring-boot:run
```

## 4. Minimal Validation

1. Send `EDIT_OP` over WS and verify `ACK(accepted_by_ingress)`.
2. After consume/apply, verify `OP_APPLIED(revision=...)`.
3. Send lagging `SYNC_OPS`; verify `SYNC_DONE(latestRevision=...)`.

## 5. Metadata Clarification

`clientClock` and `baseVector` are optional metadata for server diagnostics/watermark assistance.  
They are not independent client-side convergence vectors.


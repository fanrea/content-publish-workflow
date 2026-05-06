# Stage2 Realtime Local Validation

本指南用于本地验证协同闭环：`JOIN -> EDIT_OP -> SYNC_OPS`。

## 1. 语义前提

当前模式为 **Server-Ordered CRDT-like Merge Engine**：

- `merge mode` 可能配置为 `crdt`，但语义是服务端顺序合并，不是完整端侧 CRDT。
- `ACK(acceptedAck)` 不等于全局生效。
- `OP_APPLIED` 才表示进入全局顺序并生效。

## 2. 启动依赖

```bash
docker compose -f compose.local.yml up -d mysql redis
docker compose -f compose.local.yml --profile rocketmq up -d rocketmq-namesrv rocketmq-broker
```

## 3. 启动应用

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

## 4. 验证步骤

1. 创建文档（HTTP），记录 `docId`、`latestRevision`。
2. 发送 `JOIN`（WS），确认返回 `SNAPSHOT`，并包含 `snapshotRevision/latestRevision`。
3. 发送 `EDIT_OP`，确认先收到 `ACK(accepted_by_ingress)`，随后收到 `OP_APPLIED`。
4. 用落后 revision 发送 `SYNC_OPS`，确认收到 0..N 条 `OP_APPLIED`，最后收到 `SYNC_DONE(latestRevision)`。

## 5. 状态机校验

客户端状态应可映射为：

`INIT -> JOINING -> SNAPSHOT_READY -> EDITING -> GAP_DETECTED -> SYNCING -> EDITING`

建议检查：

- `GAP_DETECTED` 触发后必须执行 `SYNC_OPS`。
- 若返回 `REBASE_REQUIRED` 或 `SNAPSHOT_REQUIRED`，应走 rebase/snapshot 回退，不可直接继续本地写入。

## 6. 回放来源校验

`SYNC_OPS` 服务端查找顺序：

1. Redis recent ops 热窗口。
2. miss 后 operation log。

因此本地联调可分别验证：

- 热窗口命中：短断线追赶。
- 热窗口 miss：长断线追赶仍可收敛。

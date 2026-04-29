# Stage2 Realtime Local Validation

本指南用于第2阶段闭环联调：`JOIN -> EDIT_OP -> SYNC_OPS`。

## 1. 新增 realtime 配置项

以下配置均在 `src/main/resources/application.yml` 中提供默认值，并支持环境变量覆盖：

- `workflow.realtime.redis-index.enabled`  
  是否启用 Redis 房间路由/会话索引能力开关（默认：`false`，环境变量：`DOC_REALTIME_REDIS_INDEX_ENABLED`）。
- `workflow.realtime.gateway-id`  
  网关逻辑 ID（默认：`gateway-local`，环境变量：`DOC_REALTIME_GATEWAY_ID`）。
- `workflow.realtime.recent-updates.enabled`  
  是否启用最近 N 条增量热窗口（默认：`false`，环境变量：`DOC_REALTIME_RECENT_UPDATES_ENABLED`）。
- `workflow.realtime.recent-updates.size`  
  热窗口最多缓存条数（默认：`200`，环境变量：`DOC_REALTIME_RECENT_UPDATES_SIZE`）。
- `workflow.realtime.recent-updates.ttl`  
  热窗口 TTL（默认：`120s`，环境变量：`DOC_REALTIME_RECENT_UPDATES_TTL`）。

## 2. 启动依赖与服务

1. 启动 MySQL / Redis：

```bash
docker compose -f compose.local.yml up -d mysql redis
```

2. 启动 RocketMQ ingress（namesrv + broker）：

```bash
docker compose -f compose.local.yml --profile rocketmq up -d rocketmq-namesrv rocketmq-broker
```

3. 启动应用（PowerShell 示例）：

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

## 3. 闭环验证步骤

### 3.1 创建文档（HTTP）

```bash
curl -X POST "http://localhost:8080/api/docs" \
  -H "Content-Type: application/json" \
  -H "X-Editor-Id: u1" \
  -H "X-Editor-Name: alice" \
  -d "{\"docNo\":\"demo-1\",\"title\":\"demo\",\"content\":\"hello\"}"
```

记录返回 `data.id` 为 `docId`，`data.latestRevision` 为 `latestRevision`。

### 3.2 JOIN 验证（WebSocket）

发送：

```json
{"type":"JOIN","docId":<docId>,"editorId":"u1","editorName":"alice"}
```

检查点：

1. 收到 `type=SNAPSHOT`，且 `revision` 为当前最新版本。
2. 收到 `type=PRESENCE`，`message=participant joined`。

### 3.3 EDIT_OP 验证（WebSocket）

发送：

```json
{
  "type":"EDIT_OP",
  "docId":<docId>,
  "baseRevision":<latestRevision>,
  "clientSeq":1,
  "clientSessionId":"ws-client-1",
  "editorId":"u1",
  "editorName":"alice",
  "op":{"opType":"INSERT","position":5,"length":0,"text":" world"}
}
```

检查点：

1. 请求提交后立即收到 `type=ACK` 且 `message=accepted_by_ingress`。
2. 消费完成后，房间会收到 `type=OP_APPLIED`，`revision` 递增。

### 3.4 SYNC_OPS 验证（WebSocket）

发送（`staleRevision` 小于当前最新版本）：

```json
{"type":"SYNC_OPS","docId":<docId>,"baseRevision":<staleRevision>,"syncLimit":200,"editorId":"u1","editorName":"alice"}
```

检查点：

1. 先收到 0~N 条 `type=OP_APPLIED`（补发增量）。
2. 最后收到 `type=SYNC_DONE`，`message` 形如 `replayedOps=<count>`，并带 `latestRevision`。

## 4. 常见问题

- 若 `EDIT_OP` 返回 `ERROR` 且消息为 `failed to publish edit operation ingress command`，先确认：
  - `DOC_INGRESS_ROCKETMQ_ENABLED=true`
  - RocketMQ namesrv/broker 已启动
  - `INGRESS_ROCKETMQ_NAME_SERVER` 地址可达
- 若 `SYNC_OPS` 无回放，先确认 `baseRevision` 是否确实落后于当前 `latestRevision`。

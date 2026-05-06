# 协同编辑架构说明

## 1. 统一模型

协同写入模型统一为 **Server-Ordered CRDT-like Merge Engine**：

- 服务端接收操作后进入有序日志（按文档维度单调 revision）。
- 在服务端执行确定性 merge/rebase（CRDT-style 字符 ID 重定位）。
- 广播与补偿都围绕同一 revision 序列。

注意：配置名即使显示 `crdt`，语义也不是“完整端侧 CRDT”。

## 2. 写入主链路

1. HTTP/WS 入口统一映射为 `OperationCommand`。
2. ingress（RocketMQ）接收并顺序投递。
3. `DocumentActorCollaborationEngine` 执行 merge/rebase。
4. 事务内写入：
   - 文档最新内容/版本
   - revision 元数据
   - operation log（含幂等键）
5. 提交后广播 `OP_APPLIED`。

## 3. JOIN/SYNC 状态机

`INIT -> JOINING -> SNAPSHOT_READY -> EDITING -> GAP_DETECTED -> SYNCING -> EDITING`

协议语义：

- `JOIN`：返回 `snapshotRevision`、`latestRevision`。
- `EDIT_OP`：先返回 `acceptedAck`（仅入口接收成功）。
- `OP_APPLIED`：表示进入全局顺序并生效。
- `SYNC_OPS`：用于追赶 `lastAppliedRevision` 之后的增量。
- `SYNC_DONE`：返回最新 `latestRevision`。
- baseRevision 过旧：返回 `REBASE_REQUIRED` 或 `SNAPSHOT_REQUIRED`。

## 4. 一致性与回放

- Redis 广播是弱实时 fanout（低延迟优先）。
- 最终一致性不依赖 fanout 本身，依赖：
  - 客户端 `lastAppliedRevision`
  - 服务端 `SYNC_OPS` 补偿
- `SYNC_OPS` 顺序：
1. 先查 Redis recent ops。
2. miss 后查 operation log（持久层）。

## 5. 存储分层口径

- local/dev：允许 filesystem operation log adapter。
- production：推荐 MySQL + RocketMQ + Redis + OSS/MinIO snapshot。
- operation log 可实现为：
  - MySQL 分表
  - 对象存储分段文件（按文档/时间切段）

## 6. 非主链路说明

- RabbitMQ/outbox 不是当前项目主链路。
- 当前文档与面试口径应聚焦：
  - RocketMQ 顺序消息
  - DB 唯一键幂等
  - operation log 可重放补偿

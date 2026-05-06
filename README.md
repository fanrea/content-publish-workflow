# 协同文档编辑系统（Collaborative Document Service）

本项目是协同文档编辑后端，技术栈：Spring Boot + MyBatis-Plus + MySQL + Redis + RocketMQ + WebSocket。

## 概念口径（统一）

本仓库的并发编辑模型统一定义为：

- **Server-Ordered CRDT-like Merge Engine**
- 中文表述：**服务端有序写入模型 + CRDT-style 字符 ID 重定位**

说明：

- 配置项里可能仍出现 `merge mode = crdt`。
- 这里的 `crdt` 表示“CRDT-like 的服务端合并策略”，**不是完整端侧 CRDT**。
- 全局一致性由服务端顺序日志与确定性合并规则保证，而非客户端各自独立收敛。

详细规则见：[docs/SERVER_ORDERED_MERGE.md](docs/SERVER_ORDERED_MERGE.md)

## 协同状态机（JOIN/SYNC）

客户端建议按以下状态机实现：

`INIT -> JOINING -> SNAPSHOT_READY -> EDITING -> GAP_DETECTED -> SYNCING -> EDITING`

关键语义：

- `JOIN` 返回 `snapshotRevision` 与 `latestRevision`。
- `EDIT_OP` 的 `acceptedAck` 仅表示入口已接收，不代表进入全局顺序。
- 只有收到 `OP_APPLIED` 才表示该操作已进入服务端顺序并生效。
- `SYNC_DONE` 返回 `latestRevision`，客户端据此更新本地水位。
- 过旧 `baseRevision` 由服务端返回 `REBASE_REQUIRED` 或 `SNAPSHOT_REQUIRED`。

## 一致性与回放链路

- Redis 广播定位是弱实时 fanout，不作为最终一致性来源。
- 最终一致性依赖 `lastAppliedRevision + SYNC_OPS`。
- `SYNC_OPS` 查询顺序：
1. 先查 Redis recent ops 热窗口。
2. miss 后查 operation log。

## 存储与生产口径

- filesystem operation log 仅用于 local/dev adapter。
- 生产口径应为：
1. MySQL 元数据（文档、revision、幂等键等）
2. RocketMQ 顺序日志（入口与消费顺序化）
3. Redis 热窗口（recent ops / fanout）
4. OSS/MinIO snapshot（冷恢复与历史回放加速）
- operation log 在生产可落 MySQL 分表或对象存储分段文件。

更多见：[docs/storage-snapshot-roadmap.md](docs/storage-snapshot-roadmap.md)

## 中间件口径

- 当前主链路是 RocketMQ ingress + 顺序消费 + 幂等写入。
- RabbitMQ/outbox 不是当前主链路，不作为本仓库默认叙述。
- 简历/项目介绍建议口径：**RocketMQ 顺序消息 + DB 唯一键幂等 + operation log 可重放补偿**。

## 文档索引

- [docs/architecture-collab.md](docs/architecture-collab.md)
- [docs/SERVER_ORDERED_MERGE.md](docs/SERVER_ORDERED_MERGE.md)
- [docs/STAGE2_REALTIME_LOCAL.md](docs/STAGE2_REALTIME_LOCAL.md)
- [docs/INGRESS_ROCKETMQ_LOCAL.md](docs/INGRESS_ROCKETMQ_LOCAL.md)
- [docs/storage-snapshot-roadmap.md](docs/storage-snapshot-roadmap.md)

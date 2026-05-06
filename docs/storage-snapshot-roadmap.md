# 存储策略升级路线（Operation Log + Snapshot）

## 1. 目标口径

本项目协同引擎是 Server-Ordered CRDT-like Merge Engine。存储层需要支持：

- 顺序 operation log 持续追加
- 周期 snapshot 降低重放成本
- 基于 `lastAppliedRevision` 的断线补偿

## 2. 环境分层

- **local/dev**：filesystem operation log adapter 可用，仅用于开发验证。
- **production**：以 MySQL + RocketMQ + Redis + OSS/MinIO 为基线。

生产推荐分工：

1. MySQL：文档元数据、revision 元数据、幂等键状态。
2. RocketMQ：入口顺序消息与可重放消费链路。
3. Redis：recent ops 热窗口与弱实时 fanout。
4. OSS/MinIO：snapshot 与历史分段归档。

## 3. operation log 落地形态

生产可选：

1. MySQL 分表（按 documentId hash 或时间分桶）。
2. 对象存储分段文件（按文档 + 时间片滚动）。

两者都应支持按 revision 区间读取，供 `SYNC_OPS` 与恢复任务使用。

## 4. 读写路径建议

写入：

1. 服务端顺序消费并完成 merge/rebase。
2. 单事务提交文档状态 + revision 元数据 + operation log 索引信息。
3. 异步/准实时维护 snapshot。

读取/恢复：

1. 先定位最近 snapshot。
2. 回放 snapshot 之后的 operation 到目标 revision。
3. 当追赶窗口较大时，优先返回 `SNAPSHOT_REQUIRED` 给客户端。

## 5. 与实时链路的关系

- Redis fanout 不是最终一致性来源。
- 最终一致性依赖 operation log 可追赶与 `SYNC_OPS` 完整可用。
- recent ops 热窗口是优化层，不是一致性层。

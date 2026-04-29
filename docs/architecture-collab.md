# 协同文档系统架构说明（面向可追问）

## 1. 文本架构图（ASCII）

```text
       +-------------------------+             +-------------------------+
       |      HTTP Clients       |             |       WS Clients        |
       |  PUT/POST /api/docs/**  |             | JOIN/EDIT_OP/SYNC_OPS  |
       +------------+------------+             +------------+------------+
                    \                                      /
                     \                                    /
                      v                                  v
                 +-------------------------------------------+
                 |   Write Gateway (Target Unified Model)   |
                 |  - HTTP Adapter -> OperationCommand       |
                 |  - WS Adapter   -> OperationCommand       |
                 +------------------+------------------------+
                                    |
                                    v
        +---------------------------------------------------------------+
        | Operation Pipeline (Single Writer Semantics)                  |
        | 1) member/role check (owner/editor/viewer)                   |
        | 2) idempotency check (documentId + sessionId + clientSeq)    |
        | 3) OT-lite transform/rebase                                  |
        | 4) CAS update document + append revision + append operation   |
        | 5) comment anchor relocate                                    |
        | 6) publishAfterCommit(domain event)                           |
        +--------------------+--------------------------+---------------+
                             |                          |
                             v                          v
      +------------------------------------+   +------------------------+
      | MySQL                              |   | Extension Adapters     |
      | collaborative_document             |   | Redis Cache (optional) |
      | collaborative_document_revision    |   | RocketMQ (optional)    |
      | collaborative_document_operation   |   +------------------------+
      | collaborative_document_comment(*)  |
      +--------------------+---------------+
                           |
                           v
               +-------------------------------+
               | Realtime Push & Catch-up      |
               | ACK / APPLIED / PRESENCE      |
               | SYNC_OPS replay               |
               +-------------------------------+
```

## 2. 三条系统不变量

### 2.1 版本单调（Monotonic Revision）

定义：

- 对同一 `documentId`，每个成功写入必须使 `revision` 严格递增，且每个 revision 只会被提交一次。

落地点：

- 写入时使用 `baseRevision` + 乐观并发（`lock_version` + `latest_revision`）进行条件更新。
- 数据库唯一约束：
  - `collaborative_document_revision`：`(document_id, revision_no)` 唯一
  - `collaborative_document_operation`：`(document_id, revision_no)` 唯一

失败语义：

- `baseRevision` 落后或超前时拒绝写入并返回并发冲突（`DOCUMENT_CONCURRENT_MODIFICATION`）。

### 2.2 操作幂等（Operation Idempotency）

定义：

- 同一文档下，同一逻辑会话 `sessionId` + 同一 `clientSeq` 的操作最多生效一次。

落地点：

- 数据库唯一约束：`collaborative_document_operation(document_id, session_id, client_seq)`。
- 服务端先查重再写入；极端并发下命中唯一键时按重复请求返回已有结果，不重复施加副作用。

收益：

- 客户端重试、网络抖动、WS 断线重连后重复投递不会造成内容重复修改。

### 2.3 最终收敛（Eventual Convergence）

定义：

- 对同一文档，若所有客户端最终接收到同一操作序列，并使用一致的变换规则，则内容最终一致。

落地点：

- 对落后版本操作执行 OT-lite 重定位（`rebaseOperation`）。
- 同位置插入使用稳定 tie-break（`editorId` + `clientSeq`）保证确定性。
- 断线恢复通过 `SYNC_OPS` + `fromRevision` 回放增量操作。

边界说明：

- 当版本落后超过阈值（如 `MAX_REBASE_OPS`）时不尝试错误重定位，而是返回冲突并要求客户端拉取最新快照再提交。

## 3. HTTP/WS 统一写路径语义（目标态）

### 3.1 现状（As-Is）

- HTTP `PUT /api/docs/{documentId}`：已改为 `applyFullReplaceOperation -> applyOperation`，以 HTTP 适配层映射整篇替换到统一 operation pipeline。
- HTTP `POST /api/docs/{documentId}/restore`：已改为 owner 权限校验后，按目标 revision 内容走同一 operation pipeline（`changeType=RESTORE`）。
- WS `EDIT_OP`：走 `applyOperation`，以操作级（INSERT/DELETE/REPLACE）写入为主。

这会导致“同一业务写入有两条语义不同路径”，面试追问时一致性解释成本高。

### 3.2 目标态（To-Be）

核心原则：

- 所有写入入口统一编排到同一 `OperationCommand Pipeline`，共享同一套并发与幂等规则。

统一流程：

1. HTTP/WS 入口都先映射为 `OperationCommand`。
2. 做成员权限校验（`owner/editor` 可写）。
3. 执行幂等检查（`documentId + logicalSessionId + clientSeq`）。
4. 执行 OT-lite 变换与 `baseRevision` 校验。
5. 在单事务内完成：文档 CAS 更新 + revision 追加 + operation 追加。
6. 事务提交后发布领域事件（`publishAfterCommit`）。
7. 返回统一语义结果：
   - HTTP：返回最新文档 + 接受的 operation/revision 信息
   - WS：返回 ACK（给发送方）+ APPLIED（广播给其他在线成员）

### 3.3 迁移策略建议

- 第一步：保留现有 HTTP 接口形状，但内部改为“适配后调用 operation pipeline”。
- 第二步：补充显式操作接口（如 `POST /api/docs/{id}/operations`），前端逐步迁移。
- 第三步：将整篇替换路径降级为兼容入口，减少双写语义分叉。

## 4. 面试可追问点（建议准备）

- 如何证明版本单调在“并发 + 重试 + 断线”下仍成立。
- 为什么幂等键设计为 `(documentId, sessionId, clientSeq)`，而不是只用全局 requestId。
- OT-lite 的收敛边界与冲突回退策略（何时自动重定位，何时强制拉快照）。

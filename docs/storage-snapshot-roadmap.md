# 存储策略升级路线（Operation Log + Snapshot）

## 背景
当前实现中，`collaborative_document_revision.content` 每次都存整篇内容。优点是读取简单，缺点是热文档下写放大明显，且历史回放成本高。

## 目标
把“每 revision 全文落库”升级为：
- 操作日志持续追加（已有 `collaborative_document_operation`）
- 周期快照（例如每 100 个 revision 生成一次 snapshot）
- 按“最近快照 + 增量回放”恢复任意 revision

## 建议的数据模型变更
1. 在 `collaborative_document_revision` 增加 `is_snapshot`（tinyint/bool）
2. `content` 改为可空：仅 snapshot 行存全文，普通 revision 可为空
3. （可选）新增 `snapshot_from_revision` 字段，标注快照覆盖区间

## 写入流程（目标态）
1. 先按现有 pipeline 写 `document` 与 `operation`
2. 生成 revision 元数据（不强制存全文）
3. 当 `revision_no % SNAPSHOT_INTERVAL == 0` 时额外落 snapshot 全文

## 读取流程（目标态）
1. 定位目标 revision 之前最近的 snapshot
2. 从 snapshot 开始回放 operation 到目标 revision
3. 回放超过阈值时可异步补快照，控制冷读延迟

## 面试可讲指标
- 写放大：`O(full_text)` -> `O(op_size)`，快照周期性摊销
- 恢复复杂度：`O(snapshot_distance)`，由快照间隔上限控制
- 成本参数：`SNAPSHOT_INTERVAL` 作为吞吐/存储/恢复延迟的调节旋钮

## 渐进迁移建议
1. 先加字段并保持兼容（双写全文 + is_snapshot）
2. 灰度切换为“仅 snapshot 存全文”
3. 补齐回放恢复接口与一致性测试后，移除旧路径依赖

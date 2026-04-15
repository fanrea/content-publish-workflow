# Redis Cache（设计说明）

本服务使用 Spring Cache 接入 Redis，为“草稿详情/列表页/状态统计”等读多写少接口提供低侵入缓存能力，目标是降低 DB 读压与尾延迟抖动。

启用方式与配置见：
- `docs/REDIS_GUIDE.md`
- `src/main/resources/application-redis.yml`

## 缓存边界

缓存只用于“可再生数据”：
- 草稿详情（byId / byBizNo）
- 草稿分页列表（摘要，不含 body）
- 状态计数（Tab/Badge）

不把缓存当作状态机的一部分，不依赖缓存实现强一致。

## 命名与 Key 约定

命名集中在：
- `com.contentworkflow.common.cache.CacheNames`
- `com.contentworkflow.common.cache.CacheKeys`

约定：
- `cacheName` 体现用途，例如 `cpw:draft:detail:byId`
- key 只表达维度，例如 `id:123`
- 最终 Redis key = `workflow.cache.keyPrefix + cacheName + key`

## TTL 与一致性策略

采用“写后驱逐 + 短 TTL”的组合：
- 详情：默认 30s（可通过 `workflow.cache.draftDetailTtl` 覆盖）
- 列表：默认 5s（可通过 `workflow.cache.draftListTtl` 覆盖）
- 计数：默认 10s（可通过 `workflow.cache.draftStatusCountTtl` 覆盖）

原因：
- 详情读频高，短 TTL 能显著减压
- 列表/计数允许短时间近似，优先保障吞吐与稳定性

## 失效策略（当前落地）

缓存失效目前落在 Repository 层（对上层透明）：
- `findById` / `findByBizNo`：`@Cacheable`
- `save` / `deleteById`：`@CacheEvict`
  - 驱逐详情缓存（byId/byBizNo）
  - 清空计数缓存（allEntries）

这种方式的好处：
- 不改业务代码即可获得缓存收益
- 业务一致性仍以 DB 为准，缓存只做 best-effort

## 进一步增强（可选）

当业务复杂度提升后，单靠 TTL 可能不够：
- 引入 cache-invalidation outbox：将“草稿变更/发布”写入 outbox，由 worker 统一做精细化失效
- 引入预热策略：发布完成后对详情/列表页进行预热

SQL 示例见：`sql/cache_invalidation_outbox.sql`（仅生成，不自动执行）




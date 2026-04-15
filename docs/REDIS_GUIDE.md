# Redis Guide（可选缓存层）

本服务的事实来源（source of truth）始终是数据库。Redis 在本项目中的定位是**可选缓存层**，用于降低列表页/详情页热点读取压力，平滑 P95/P99 延迟波动。

当前实现方式：通过 `redis` profile 启用 Spring Cache（见 `src/main/resources/application-redis.yml`）。

## 如何启用

启用 profile：

```bash
SPRING_PROFILES_ACTIVE=redis
```

常用环境变量：
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `REDIS_DB`

启用后行为：
- `spring.cache.type` 切换为 `redis`
- readiness 探针会把 Redis 作为必须依赖（`ping + db + redis`）

## 缓存策略建议（不改变业务语义）

建议缓存“可再生数据”，避免让缓存成为状态机的一部分：
- 草稿详情（按 `draftId` / `bizNo`）：适合读多写少，TTL 建议 30s 级别
- 草稿列表（分页/筛选）：适合高频刷新，TTL 建议 3-10s 级别
- 状态计数（Tab/Badge）：允许短时间近似，TTL 建议 5-30s 级别

不建议缓存（或只做极短 TTL）：
- 对一致性要求极强、写后必须立刻可见的状态类数据
- 强审计类“历史记录列表”（review/snapshot/log）除非有明确的失效方案

## Key 约定（项目内已落地）

命名集中在：
- `com.contentworkflow.common.cache.CacheNames`
- `com.contentworkflow.common.cache.CacheKeys`

约定：
- `cacheName` 体现业务域与用途，例如 `cpw:draft:detail:byId`
- key 只表达维度，例如 `id:123`
- 最终 Redis key = `keyPrefix + cacheName + key`

`keyPrefix` / TTL 可通过环境变量覆盖（见 `application-redis.yml` 的 `workflow.cache.*`）。

## 失效策略（当前落地方式）

本项目采用“写后驱逐 + 短 TTL”组合：
- 详情查询使用 `@Cacheable`
- 保存/删除时使用 `@CacheEvict` 驱逐详情与统计缓存

好处是无需改业务代码即可获得收益，同时把一致性复杂度控制在可接受范围内。

## 运维注意

- 仅用于本地演示时，不建议开启 `redis` profile（否则 readiness 会强依赖 Redis）。
- 生产建议补齐 Redis 连接池（需要 `commons-pool2` 依赖）、超时、降级策略。




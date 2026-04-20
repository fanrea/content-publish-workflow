# Redis Guide

这份文档只讲 Redis 在当前项目里的实际作用和启用方式，不展开缓存实现细节。

## 先说结论

不开 `redis` profile 时：

- 项目仍然能跑
- 默认使用 Caffeine 本地缓存
- readiness 不会检查 Redis

开了 `redis` profile 时：

- Spring Cache 切换到 Redis
- readiness 会把 Redis 纳入必须依赖
- 草稿详情、草稿列表、状态统计等缓存会走 Redis

所以 Redis 在这个项目里是“增强项”，不是默认启动前提。

## 本地怎么启用

### 1. 启动 Redis

```powershell
docker compose -f compose.local.yml up -d redis
```

默认连接参数：

- Host: `127.0.0.1`
- Port: `6379`
- Password: 空
- DB: `0`

### 2. 启动应用时打开 profile

```powershell
$env:SPRING_PROFILES_ACTIVE="redis"
mvn spring-boot:run
```

如果你同时还要本地联调 RabbitMQ 和 demo 登录，常见组合是：

```powershell
$env:SPRING_PROFILES_ACTIVE="redis,rabbitmq,demo,ops"
mvn spring-boot:run
```

## 默认缓存行为

当前主配置里缓存相关的默认值是：

- 草稿详情 TTL: `30s`
- 草稿列表 TTL: `5s`
- 状态统计 TTL: `10s`

Redis profile 下还带有 Lettuce 连接池配置，适合本地并发联调：

- `max-active=16`
- `max-idle=16`
- `min-idle=0`
- `max-wait=2s`

## readiness 行为变化

不开 `redis` profile 时：

- readiness 只看 `ping` 和 `db`

开了 `redis` profile 时：

- readiness 会看 `ping`、`db`、`redis`

所以一个常见现象是：

- 应用启动成功
- 但 `/actuator/health/readiness` 返回 `DOWN`

这通常不是应用主链路坏了，而是 Redis 不可用。

## 排障顺序

### 1. Redis profile 开了，但 readiness 是 `DOWN`

先查：

```powershell
docker compose -f compose.local.yml ps
```

再看：

- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `REDIS_DB`

本地最常见原因就是 Redis 容器没起来，或者你开了 profile 却没启动 Redis。

### 2. 缓存看起来没生效

先确认两件事：

1. 你是否真的开了 `redis` profile
2. 你看的接口是不是已经接了 Spring Cache

不要先入为主把问题归到 Redis。

### 3. 数据更新后，列表还是旧的

这是短 TTL 缓存下的正常现象，不是一定有 bug。

当前列表和统计缓存都允许非常短时间的旧数据窗口，换的是查询性能和实现简洁。

### 4. 配了连接池参数，但好像不生效

这个项目已经在 `pom.xml` 里引入了 `commons-pool2`，所以 Lettuce pool 配置是可生效的。

如果你后面删掉了这个依赖，再配池参数就只是“写在配置里”。

## 本地验证建议

建议按这个顺序验证：

1. 先不开 `redis`，确认主业务能跑
2. 再开 `redis` profile
3. 观察 `/actuator/health/readiness`
4. 重复访问草稿详情、列表、统计接口
5. 再观察日志和 Redis key 变化

## 相关文档

- `docs/OPERATIONS.md`
- `docs/TESTING.md`

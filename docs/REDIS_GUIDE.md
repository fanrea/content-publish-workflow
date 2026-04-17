# Redis 启用与联调说明

这份文档更偏使用说明，重点告诉你怎么把 Redis 接进当前项目，以及启用后会有哪些行为变化。

## 一、启用步骤

### 1. 启动 Redis

确保本地或测试环境有可用 Redis。

### 2. 打开 profile

```bash
SPRING_PROFILES_ACTIVE=redis
```

如果与 MySQL 一起使用：

```bash
SPRING_PROFILES_ACTIVE=mysql,redis
```

### 3. 配置连接参数

常见环境变量：

- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `REDIS_DB`

## 二、启用后的变化

启用 `redis` 后，系统会发生这些变化：

- Spring Cache 切换到 Redis
- readiness 会检查 Redis
- 部分查询接口可走 Redis 缓存
- 消息消费去重能力更适合多实例场景

## 三、你可以重点验证什么

### 1. 草稿详情接口

重复访问：

- `GET /drafts/{draftId}`
- `GET /drafts/{draftId}/summary`

观察：

- 响应是否更稳定
- Redis 中是否出现对应缓存键

### 2. 草稿状态统计

重复访问：

- `GET /drafts/stats`

观察：

- 是否命中短 TTL 的统计缓存

### 3. 消费去重

如果消息链路开启，可以观察消费端是否利用缓存避免重复处理。

## 四、健康检查变化

默认情况下 readiness 只检查：

- `ping`
- `db`

启用 Redis 后会变为：

- `ping`
- `db`
- `redis`

因此如果 Redis 不可用，服务可能启动成功，但 readiness 会失败。

## 五、常见问题

### 1. 为什么配置了连接池参数但不生效

因为 Redis 使用 Lettuce 连接池时，需要 `commons-pool2` 依赖。项目已经在 `pom.xml` 里引入了这部分依赖。

### 2. 为什么 Redis 挂了，接口还是能返回

因为缓存错误处理器默认只记录日志，不会让主业务直接失败。

### 3. 为什么数据改了，列表页短时间还是旧的

因为列表和统计缓存是允许短 TTL 过期的，设计上接受非常短时间的轻微滞后。

## 六、联调建议

- 先用默认模式跑通接口
- 再切到 `mysql,redis`
- 观察缓存命中、readiness 和日志变化
- 不建议一上来就同时打开 MQ、XXL-Job、Redis，排障会更复杂

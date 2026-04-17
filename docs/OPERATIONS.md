# 运行与运维说明

这份文档说明项目如何启动、各个 profile 的作用是什么，以及实际排障时应该从哪里看配置和状态。

## 一、启动前提

### 1. 最小依赖

如果只是本地查看接口和流程：

- JDK 17

这时项目可以直接使用默认 H2 内存库。

### 2. 近生产演示依赖

如果要完整演示持久化、缓存、异步消息和运维能力，建议准备：

- JDK 17
- MySQL 8.x
- Redis
- RabbitMQ
- XXL-Job Admin（可选）

## 二、默认启动行为

未启用额外 profile 时，系统默认行为如下：

- 数据源为 H2 内存库
- 自动执行 `classpath:sql/schema.sql`
- `spring.cache.type=simple`
- readiness 默认只检查 `ping` 和 `db`
- RabbitMQ 不会在启动时主动连通
- 本地 `@Scheduled` 轮询默认开启

这个模式适合：

- 快速跑通接口
- 代码调试
- 单元测试和持久化测试

## 三、各个 profile 的作用

### 1. `mysql`

作用：

- 切换为 MySQL 数据源
- 使用仓库根目录的 `sql/schema.sql` 作为真实建表脚本参考
- `ddl-auto=validate`
- 不自动执行 SQL 初始化

启用前你需要先导入表结构：

```bash
mysql -uroot -p cpw < sql/schema.sql
```

### 2. `redis`

作用：

- 启用 Redis 作为 Spring Cache 实现
- readiness 额外检查 Redis
- 草稿详情、列表、状态统计等接口可以使用 Redis 缓存

### 3. `rabbitmq`

作用：

- 提供 RabbitMQ 连接参数
- 启用 outbox 配置对象
- 可选启用拓扑声明

注意：

- 仅打开 `rabbitmq` profile 不代表一定会发送消息
- 是否真正写 outbox、是否真正 relay、是否真正消费，还取决于 `workflow.outbox.*` 配置

### 4. `ops`

作用：

- 开启更多 Actuator 端点
- 可把管理端口拆出来
- 打开请求直方图等运维配置
- 更适合演示监控和排障能力

### 5. `loadtest`

作用：

- 提高 Tomcat 线程和连接能力
- 降低无关 SQL 日志干扰
- 打开监控相关分位数配置

### 6. `xxl-job`

作用：

- 注册 XXL-Job 执行器
- 关闭本地定时轮询
- 由调度中心来触发任务轮询和扫描

## 四、常见组合

### 1. 本地最小模式

```bash
SPRING_PROFILES_ACTIVE=
```

### 2. MySQL + Redis

```bash
SPRING_PROFILES_ACTIVE=mysql,redis
```

### 3. 演示完整链路

```bash
SPRING_PROFILES_ACTIVE=mysql,redis,rabbitmq,ops
```

### 4. 使用 XXL-Job

```bash
SPRING_PROFILES_ACTIVE=mysql,redis,rabbitmq,ops,xxl-job
```

## 五、关键环境变量

### 1. 数据库

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `DB_DRIVER`

### 2. Redis

- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `REDIS_DB`

### 3. RabbitMQ

- `RABBIT_HOST`
- `RABBIT_PORT`
- `RABBIT_USER`
- `RABBIT_PASSWORD`
- `RABBIT_VHOST`

### 4. 服务端口

- `SERVER_PORT`
- `MANAGEMENT_PORT`

### 5. Outbox

- `WORKFLOW_OUTBOX_EXCHANGE`
- `WORKFLOW_OUTBOX_ROUTING_KEY_PREFIX`
- `workflow.outbox.enabled`
- `workflow.outbox.relay.enabled`

### 6. 缓存

- `CACHE_PREFIX`
- `CACHE_DRAFT_DETAIL_TTL`
- `CACHE_DRAFT_LIST_TTL`
- `CACHE_DRAFT_STATUS_COUNT_TTL`

### 7. XXL-Job

- `XXL_JOB_ADMIN_ADDRESSES`
- `XXL_JOB_ACCESS_TOKEN`
- `XXL_JOB_EXECUTOR_APPNAME`
- `XXL_JOB_EXECUTOR_PORT`

## 六、建表与初始化

### 1. H2

默认情况下，Spring Boot 会自动执行：

- `src/main/resources/sql/schema.sql`

### 2. MySQL

MySQL 模式下不会自动执行建表脚本，需要手动执行：

- `sql/schema.sql`

如果要准备演示数据，可以继续执行：

- `sql/seed_demo.sql`

## 七、健康检查与监控

默认暴露：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/metrics`
- `/actuator/prometheus`

默认 readiness：

- `ping`
- `db`

启用 `redis` 后：

- `ping`
- `db`
- `redis`

## 八、Worker 运行方式

### 1. 本地 `@Scheduled`

默认开启本地调度：

- `PublishTaskWorker`
- `OutboxRelayWorker`

适合本地开发和最小演示。

### 2. XXL-Job 调度

当启用 `xxl-job` 后，本地轮询会关闭，由以下 job 触发：

- `workflowPublishTaskPollJob`
- `workflowOutboxRelayJob`
- `workflowDeadPublishTaskScanJob`
- `workflowDeadOutboxScanJob`

## 九、常见排障路径

### 1. 接口发布成功，但页面一直没完成

优先检查：

- `/drafts/{draftId}/tasks`
- `/drafts/{draftId}/logs/publish-timeline`

看点：

- 是否仍有 `PENDING` 或 `RUNNING` 任务
- 是否出现 `FAILED` / `DEAD`

### 2. MySQL 启动失败

优先检查：

- 是否先执行了 `sql/schema.sql`
- `DB_URL` 是否正确
- `ddl-auto=validate` 是否发现列或索引不一致

### 3. Redis 打开后 readiness 失败

优先检查：

- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- Redis 是否可达

### 4. Outbox 一直积压

优先检查：

- 是否启用了 `workflow.outbox.enabled`
- 是否启用了 `workflow.outbox.relay.enabled`
- RabbitMQ 是否可连接
- `workflow_outbox_event` 表中事件状态是否卡在 `FAILED` 或 `DEAD`

### 5. 恢复接口不可用

优先检查：

- 当前角色是否有对应权限
- 任务是否属于当前 `publishedVersion`
- 任务状态是否是 `FAILED` 或 `DEAD`

## 十、建议的演示方式

如果要给别人演示这套系统，建议按这个顺序：

1. 用默认模式启动，说明最小依赖
2. 切换到 MySQL + Redis
3. 演示发布、失败、恢复链路
4. 打开 `ops` 展示健康检查和指标
5. 如有需要，再接入 XXL-Job 演示调度

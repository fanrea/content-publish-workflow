# 运行与运维说明

本文档基于当前工作区源码与配置编写，目标是两件事：

1. 让第一次接手项目的人能尽快把服务跑起来。
2. 让排障时能快速定位是数据库、Flyway、JWT、缓存、任务调度、Outbox、XXL-Job 还是健康检查出了问题。

## 1. 当前默认运行基线

当前主应用默认已经不是 H2 路线，而是：

- MySQL
- Flyway
- MyBatis-Plus
- 本地缓存
- 本地 `@Scheduled` 轮询任务
- JWT 鉴权

不显式开启任何 profile 时，`src/main/resources/application.yml` 就会生效，默认配置是：

- `spring.datasource.url=jdbc:mysql://127.0.0.1:3306/cpw...`
- `spring.datasource.username=cpw`
- `spring.datasource.password=cpw`
- `spring.sql.init.mode=never`
- `spring.flyway.enabled=true`
- `spring.cache.type=caffeine`
- `workflow.scheduler.local.enabled=true`
- `workflow.security.login.demo-users=[]`

这意味着：

- 主应用运行时的表结构来源是 Flyway migration，不是默认 H2
- 不开 `demo` profile 时，登录接口存在，但没有内置可登录账号
- 不开 `redis` profile 时，缓存仍然可用，但只走本地缓存
- 不开 `rabbitmq` profile 时，Outbox 发布器不会启用，业务事件不会写入 outbox

H2 仍然存在，但只在测试与嵌入式数据库初始化场景使用：

- `src/test/resources/application.yml` 把数据源切到 H2
- `src/main/resources/sql/schema.sql` 只给嵌入式数据库初始化使用

## 2. 本地依赖与 `compose.local.yml`

仓库根目录的 `compose.local.yml` 定义了本地最常用依赖：

| 组件 | 端口 | 说明 |
| --- | --- | --- |
| MySQL 8.4 | `3306` | 默认库 `cpw`，账号 `cpw/cpw`，root 密码 `root` |
| Redis 7.2 | `6379` | 无密码，默认 DB `0` |
| RabbitMQ 3.13 | `5672` | 默认账号 `cpw/cpw` |
| RabbitMQ 管理台 | `15672` | 观察交换机、队列、积压最方便 |
| XXL-Job Admin | `8088` | 只在 compose profile `xxl-job-admin` 下启动 |

依赖服务状态先看：

```powershell
docker compose -f compose.local.yml ps
```

## 3. 快速启动

### 3.1 最小启动组合：主应用 + MySQL

这是当前默认主路径，也是最适合先确认接口、数据库和 Flyway 是否正常的一组。

1. 启动 MySQL

```powershell
docker compose -f compose.local.yml up -d mysql
```

2. 启动应用

```powershell
$env:SPRING_PROFILES_ACTIVE=""
mvn spring-boot:run
```

此时会发生的事情：

- 应用连接 MySQL
- Flyway 自动执行 `classpath:db/migration/mysql`
- 缓存走本地缓存
- 发布任务 worker 走本地 `@Scheduled`
- readiness 只检查 `ping,db`
- Redis、RabbitMQ、demo 用户、XXL-Job 都不会启用

### 3.2 推荐本地联调组合：MySQL + Redis + RabbitMQ + demo + ops

如果要联调登录、缓存、Outbox、管理端点和观测能力，推荐这组：

1. 启动依赖

```powershell
docker compose -f compose.local.yml up -d mysql redis rabbitmq
```

2. 启动应用

```powershell
$env:SPRING_PROFILES_ACTIVE="redis,rabbitmq,demo,ops"
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
mvn spring-boot:run
```

3. 如果还想让本地 stub consumer 也消费消息，再加：

```powershell
$env:WORKFLOW_OUTBOX_TOPOLOGY_CONSUMER_ENABLED="true"
```

这组配置最贴近完整联调链路：

- Redis 纳入缓存与 readiness
- RabbitMQ 拓扑声明打开
- 业务事件会写入 outbox
- relay worker 会把消息从 outbox 发到 RabbitMQ
- `demo` profile 提供可登录账号
- `ops` profile 开启独立管理端口与更多 actuator 端点

### 3.3 XXL-Job 演示组合

只有在你明确要演示“调度中心模式”时，才建议接入 XXL-Job。

1. 启动 MySQL、Redis、RabbitMQ
2. 准备 `xxl_job` 数据库和 XXL-Job Admin 所需表
3. 启动 admin

```powershell
docker compose -f compose.local.yml --profile xxl-job-admin up -d xxl-job-admin
```

4. 启动应用

```powershell
$env:SPRING_PROFILES_ACTIVE="redis,rabbitmq,demo,ops,xxl-job"
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
$env:XXL_JOB_ADMIN_ADDRESSES="http://127.0.0.1:8088/xxl-job-admin"
mvn spring-boot:run
```

注意两点：

- `application-xxl-job.yml` 默认 admin 地址是 `http://127.0.0.1:8080/xxl-job-admin`
- 但 `compose.local.yml` 暴露给宿主机的是 `8088`

所以本地接 compose 版 XXL-Job Admin 时，几乎一定要覆盖 `XXL_JOB_ADMIN_ADDRESSES`。

## 4. 配置文件、profile 与脚本之间的关系

这一部分最容易被混淆。很多误解都来自把“示例文件”“测试脚本”“手工初始化脚本”和“主应用启动路径”混在一起。

| 文件 | 是否自动参与主应用启动 | 当前职责 |
| --- | --- | --- |
| `compose.local.yml` | 否 | 启动本地依赖容器 |
| `src/main/resources/application.yml` | 是 | 主配置；默认 MySQL + Flyway + 本地缓存 + 本地调度 + JWT |
| `src/main/resources/application-mysql.yml` | 仅在 `mysql` profile | 显式声明与默认主路径一致的 MySQL/Flyway 配置 |
| `src/main/resources/application-redis.yml` | 仅在 `redis` profile | 打开 Redis 连接、切换缓存模式、把 Redis 纳入 readiness |
| `src/main/resources/application-rabbitmq.yml` | 仅在 `rabbitmq` profile | 打开 RabbitMQ 连接、启用 outbox publisher、声明 MQ 拓扑，但 relay 默认关闭 |
| `src/main/resources/application-ops.yml` | 仅在 `ops` profile | 管理端口改到 `8081`，扩展 actuator 暴露，打开 access log |
| `src/main/resources/application-loadtest.yml` | 仅在 `loadtest` profile | 压测时调大 Tomcat 参数和指标标签 |
| `src/main/resources/application-xxl-job.yml` | 仅在 `xxl-job` profile | 打开 XXL-Job executor，并关闭本地 `@Scheduled` |
| `src/main/resources/application-demo.yml` | 仅在 `demo` profile | 提供 demo 登录账号 |
| `src/main/resources/application-example.yml` | 否 | 示例配置，不会自动加载 |
| `src/main/resources/db/migration/mysql/V*.sql` | 是 | 主应用在 MySQL 下真正执行的数据库迁移 |
| `src/main/resources/sql/schema.sql` | 否，除非嵌入式 DB init | H2/嵌入式数据库初始化脚本 |
| `sql/schema.sql` | 否 | 手工重建本地 MySQL 结构的脚本，不是运行时迁移源 |
| `sql/seed_demo.sql` | 否 | 手工灌演示数据脚本 |

### 4.1 配置叠加顺序

可以按这个顺序理解：

1. `application.yml` 先提供默认主路径
2. profile 对应的 `application-*.yml` 再叠加覆盖
3. 环境变量最后覆盖同名配置

例如：

- 开 `redis` profile 后，`spring.cache.type` 会从 `caffeine` 变成 `redis`
- 开 `ops` profile 后，management 端口会从默认应用端口切到 `8081`
- 开 `xxl-job` profile 后，`workflow.scheduler.local.enabled` 会从 `true` 变成 `false`

### 4.2 `application-demo.yml` 与 `application-example.yml` 的区别

- `application-demo.yml` 是真正会被 profile 加载的配置，作用是注入 demo 账号
- `application-example.yml` 只是示例模板，不会自动生效

运维上要记住：

- 改 `application-example.yml` 不会改变当前服务行为
- 改 `application-demo.yml` 才会影响 `demo` profile 下的实际登录用户

### 4.3 Flyway migration、schema 脚本与 seed 脚本的边界

当前源码里有三套“看起来都像 schema”的东西，但用途完全不同：

1. `db/migration/mysql/V1__init_schema.sql`、`V2__...`、`V3__...`
   这是主应用启动时真正执行的 MySQL 迁移。
2. `src/main/resources/sql/schema.sql`
   这是 H2 兼容脚本，只给嵌入式数据库初始化使用。
3. `sql/schema.sql`
   这是手工重建本地 MySQL 的开发脚本，不会被 Spring Boot 或 Flyway 自动执行。

`sql/seed_demo.sql` 的定位也要单独记住：

- 它是手工造数脚本
- 适合演示列表、统计、缓存命中、分页等接口
- 它依赖你已经有表结构

推荐原则：

- 正常本地启动和联调，优先让 Flyway 自己建表
- 只有你想手工重置、审表或造演示数据时，才用根目录 `sql/*.sql`
- 不要把 H2 `schema.sql` 当成主应用 MySQL 的真实结构来源

## 5. Profile 说明

| Profile | 打开后做什么 | 依赖什么 | 典型用途 | 关键注意点 |
| --- | --- | --- | --- | --- |
| `mysql` | 显式启用 MySQL/Flyway 配置 | MySQL | 部署时把数据库类型写得更直观 | 与默认主路径基本一致，不开也默认是 MySQL |
| `redis` | 启用 Redis 连接与缓存、readiness 检查 Redis | Redis | 联调共享缓存、两级缓存、readiness | 开了它就要求 Redis 可达 |
| `rabbitmq` | 启用 RabbitMQ 连接、outbox publisher、Rabbit 拓扑声明 | RabbitMQ | 联调事件投递链路 | 只开它还不会真正发 MQ，需要额外打开 relay |
| `ops` | 管理端口改到 `8081`，扩展 actuator 暴露，打开 access log | 无强依赖 | 本地排障、演示、压测前检查 | 开了后 actuator 主要看 `8081` |
| `loadtest` | 调整 Tomcat 线程、连接数、指标标签 | 视场景 | 压测 | 不建议平时开发默认开着 |
| `xxl-job` | 启用 XXL-Job executor，关闭本地调度 | XXL-Job Admin | 演示或接调度中心 | 要注册 handler，且不要和本地调度混开 |
| `demo` | 注入 demo 登录账号 | 无 | 本地演示、快速拿 token | 当前只提供 `admin/reviewer/editor`，没有 `operator` 账号 |

推荐组合：

- 最小可运行：不加 profile，或显式 `mysql`
- 本地完整联调：`redis,rabbitmq,demo,ops`
- 调度中心演示：`redis,rabbitmq,demo,ops,xxl-job`
- 压测环境：视情况在上面基础上加 `loadtest`

## 6. 数据库与 Flyway

### 6.1 默认数据库配置

当前主应用默认直连 MySQL，而不是先起 H2 再切换：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/cpw ...
    username: cpw
    password: cpw
  flyway:
    enabled: true
    locations: classpath:db/migration/mysql
```

### 6.2 当前 Flyway 迁移序列

当前源码里的 MySQL 迁移是：

1. `V1__init_schema.sql`
2. `V2__add_outbox_trace_request_columns.sql`
3. `V3__add_publish_task_trace_columns.sql`

可以简单理解为：

- `V1` 建主表、任务表、日志表、outbox 表和索引
- `V2` 给 `workflow_outbox_event` 增加 `trace_id`、`request_id`
- `V3` 给 `content_publish_task` 增加 `trace_id`、`request_id`

### 6.3 `baseline-on-migrate=true` 的意义

主配置里开了：

```yaml
spring:
  flyway:
    baseline-on-migrate: true
```

它的目的，是让接管已有库结构时更平滑。

但这不等于“任何脏库都能自动修好”。对于本地环境，仍然推荐：

- 首次联调优先用一套干净的 MySQL 数据卷
- 不要把手工改过结构的旧库长期复用

### 6.4 手工脚本什么时候用

根目录 `sql/schema.sql` 和 `sql/seed_demo.sql` 适合这类场景：

- 你想快速重建一个本地 MySQL 结构用于 review
- 你想手工灌一批演示数据
- 你想验证列表、统计、缓存命中、分页等接口在大一点数据量下的表现

执行顺序通常是：

1. `sql/schema.sql`
2. `sql/seed_demo.sql`

但这是一条手工维护路径，不是主应用推荐启动路径。

## 7. 安全、登录与 JWT

### 7.1 鉴权边界

当前安全规则的核心是：

- `/api/auth/login` 允许匿名访问
- `/actuator/health/**` 允许匿名访问
- `/api/workflows/**` 必须带 Bearer Token
- 其他路径默认放行

登录成功后返回的是：

- `tokenType`
- `accessToken`
- `expiresAt`
- `operatorId`
- `operatorName`
- `roles`

登录接口：

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "Admin123!"
}
```

### 7.2 JWT 关键配置

当前 JWT 配置项在 `workflow.security.jwt.*` 下：

- `issuer`
- `secret`
- `clock-skew-seconds`
- `access-token-ttl`

运维上要注意：

- `WORKFLOW_JWT_SECRET` 必须在所有实例上一致
- 修改 secret 后，旧 token 会全部失效
- secret 最好至少 32 字节
- 默认 TTL 是 `2h`
- 默认时钟漂移容忍 `60s`

### 7.3 `demo` profile 提供的账号

当前 `application-demo.yml` 内置了三个账号：

| 用户名 | 密码 | 角色 | 适合做什么 |
| --- | --- | --- | --- |
| `admin` | `Admin123!` | `ADMIN` | 全量演示、恢复、Outbox 管理 |
| `reviewer` | `Reviewer123!` | `REVIEWER` | 审核流转 |
| `editor` | `Editor123!` | `EDITOR` | 草稿创建、编辑、提审 |

关键发现：

- 当前没有内置 `OPERATOR` demo 账号
- 发布、回滚、下线、任务恢复这类动作更适合直接用 `admin`
- 当前登录实现只接了 `WorkflowDemoAccountAuthenticationProvider`
- 也就是说，源码里现在并没有接数据库用户表或外部 IAM 的登录实现

### 7.4 401 和 403 怎么理解

- `401`：没有 token，或 token 无效
- `403`：已经登录，但角色/权限不满足接口要求

错误响应体里会带：

- `code`
- `message`
- `traceId`
- `requestId`
- `path`

这对排障很有用。

## 8. 缓存模式

### 8.1 纯本地缓存模式

不打开 `redis` profile 时：

- `spring.cache.type=caffeine`
- `RedisCacheConfig` 会返回本地 `LocalTierCacheManager`
- readiness 不检查 Redis

这是默认模式，适合：

- 单机开发
- 最小启动
- 先看主链路是否通

### 8.2 Redis / 两级缓存模式

打开 `redis` profile 后：

- `spring.cache.type=redis`
- `RedisCacheConfig` 会创建本地 L1 + Redis L2 的 `TwoLevelCacheManager`
- readiness 变成 `ping,db,redis`

当前实现里有一个很容易误判的点：

- 配置里存在 `workflow.cache.two-level-enabled`
- 但当前源码并没有用这个字段决定是否装配两级缓存
- 真正决定是否切到 Redis/L1+L2 的，是 `spring.cache.type`

所以在当前版本里：

- 想回到纯本地缓存，最稳妥的做法是不要开 `redis` profile
- 只改 `CACHE_TWO_LEVEL_ENABLED=false`，并不能保证退回纯本地

### 8.3 当前 TTL 与 key 前缀

缓存相关默认值：

- `workflow.cache.keyPrefix=cpw:`
- 草稿详情 `30s`
- 草稿列表 `5s`
- 草稿状态统计 `10s`
- 其他缓存默认 `10m`

### 8.4 什么时候选哪种模式

- 只想跑通接口：纯本地缓存
- 想验证共享缓存或 Redis readiness：开 `redis`
- 想演示两级缓存：开 `redis`，并明确说明当前实现是本地 L1 + Redis L2

## 9. 调度模式

### 9.1 默认调度模式：本地 `@Scheduled`

默认情况下：

- `workflow.scheduler.local.enabled=true`
- `xxl.job.executor.enabled=false`

会由本地定时轮询触发：

- `PublishTaskWorker`
- `OutboxRelayWorker`，前提是 relay bean 已开启

这就是单机开发的默认模式。

### 9.2 XXL-Job 模式

打开 `xxl-job` profile 后：

- `workflow.scheduler.local.enabled=false`
- `xxl.job.executor.enabled=true`

当前需要注册的 handler 名称是：

1. `workflowPublishTaskPollJob`
2. `workflowOutboxRelayJob`
3. `workflowDeadPublishTaskScanJob`
4. `workflowDeadOutboxScanJob`

源码里的行为是：

- 发布任务轮询由 `workflowPublishTaskPollJob` 触发
- Outbox relay 由 `workflowOutboxRelayJob` 触发
- 死任务扫描与死 outbox 扫描由另外两个 handler 触发

### 9.3 为什么不要混开

源码里专门有 `WorkflowSchedulerModeConfiguration` 在启动时打印模式：

- `LOCAL_FALLBACK`
- `XXL_JOB`
- `HYBRID`
- `NONE`

其中：

- `HYBRID` 表示本地调度和 XXL-Job 同时打开，可能双触发
- `NONE` 表示两个都没开，任务根本不会跑

运维上要坚持：

- 本地开发用 `LOCAL_FALLBACK`
- 接调度中心时用 `XXL_JOB`
- 不要接受 `HYBRID`

## 10. MQ、Outbox、健康检查与观测

### 10.1 Outbox 的真实开关关系

数据库里无论如何都会有 `workflow_outbox_event` 表，因为表结构在 Flyway 里。

但“表存在”不等于“Outbox 功能已经启用”。

当前真实逻辑是：

- 不开 `rabbitmq` profile 时，`workflow.outbox.enabled=false`
- 此时 `WorkflowEventPublisher` 会退化成 `NoopWorkflowEventPublisher`
- 业务事件不会写入 outbox，也不会发 MQ

打开 `rabbitmq` profile 后才会变成：

- `workflow.outbox.enabled=true`
- `workflow.outbox.topology.enabled=true`
- `workflow.outbox.relay.enabled=false`
- `workflow.outbox.topology.consumer.enabled=false`

也就是：

- 业务事件会写入 outbox
- 会声明 exchange / queue / binding
- 但默认不真正 relay 到 RabbitMQ
- 默认也不启动 demo consumer

### 10.2 想真正把消息发到 RabbitMQ，还要开哪些开关

必须再打开：

```powershell
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
```

如果还要让 stub consumer 收到消息，再打开：

```powershell
$env:WORKFLOW_OUTBOX_TOPOLOGY_CONSUMER_ENABLED="true"
```

### 10.3 当前拓扑

当前代码会声明：

- exchange: `cpw.workflow.events`
- queue: `cpw.workflow.search-index.refresh`
- queue: `cpw.workflow.read-model.sync`
- queue: `cpw.workflow.publish.notification`

consumer 默认只是日志化处理和 stub 消费，不代表真实下游系统已经接入。

### 10.4 健康检查

基础健康检查端点：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

当前 readiness 逻辑：

- 默认：`ping,db`
- 开 `redis` profile：`ping,db,redis`

注意：

- RabbitMQ 不在 readiness 组里
- XXL-Job Admin 也不在 readiness 组里

所以：

- readiness 失败通常先看数据库和 Redis
- RabbitMQ 链路断了，不一定会让 readiness 直接失败

### 10.5 日志与观测

默认控制台日志格式包含：

- `traceId`
- `requestId`

业务请求还会通过过滤器把下面两个响应头回传给调用方：

- `X-Trace-Id`
- `X-Request-Id`

默认 actuator 暴露：

- `health`
- `info`
- `metrics`
- `prometheus`

打开 `ops` profile 后会扩展：

- `loggers`
- `env`
- `configprops`
- `threaddump`
- `httpexchanges`

并且：

- management 端口切到 `8081`
- `health.show-details=always`
- Tomcat access log 打开

### 10.6 排障时最有价值的业务接口

这些接口通常比直接翻日志更快：

- `GET /api/workflows/drafts/{draftId}/tasks`
- `GET /api/workflows/drafts/{draftId}/commands`
- `GET /api/workflows/drafts/{draftId}/logs`
- `GET /api/workflows/drafts/{draftId}/logs/timeline?traceId=...`
- `GET /api/workflows/drafts/{draftId}/logs/publish-timeline?publishedVersion=...`
- `GET /api/workflows/drafts/{draftId}/recovery/tasks`
- `GET /api/workflows/outbox/events/recovery`

其中 Outbox 恢复接口要求 `ADMIN` 权限。

## 11. 推荐组合

### 11.1 推荐的本地联调组合

```powershell
docker compose -f compose.local.yml up -d mysql redis rabbitmq
$env:SPRING_PROFILES_ACTIVE="redis,rabbitmq,demo,ops"
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
mvn spring-boot:run
```

为什么这么配：

- MySQL 是当前默认主路径
- Redis 能让缓存、readiness 和两级缓存路径都跑起来
- RabbitMQ 能把 Outbox 路径跑通
- `demo` 能快速拿 token
- `ops` 能快速看到健康检查和配置

### 11.2 推荐的演示组合

如果面向演示或答辩，推荐：

```powershell
docker compose -f compose.local.yml up -d mysql redis rabbitmq
$env:SPRING_PROFILES_ACTIVE="redis,rabbitmq,demo,ops"
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
$env:WORKFLOW_OUTBOX_TOPOLOGY_CONSUMER_ENABLED="true"
mvn spring-boot:run
```

演示账号直接用：

- `admin / Admin123!`

原因：

- `admin` 具备全部权限
- 既能演示发布、回滚、下线，也能演示任务恢复和 Outbox 恢复

### 11.3 推荐的调度中心演示组合

只有需要展示“调度中心模式”时，才追加：

- `xxl-job` profile
- `xxl-job-admin` 容器
- 正确的 `XXL_JOB_ADMIN_ADDRESSES`

否则默认本地调度更简单，也更接近单机开发体验。

## 12. 常见排障手册

### 12.1 数据库问题

现象：

- 应用启动就报数据源连接错误
- `/actuator/health/readiness` 中 `db` 为 `DOWN`

先查：

```powershell
docker compose -f compose.local.yml ps
```

重点确认：

- MySQL 容器是不是 healthy
- `3306` 端口有没有被别的实例占用
- 是否误覆盖了 `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`

### 12.2 Flyway 问题

现象：

- 启动时报 migration 校验失败
- 已有表结构和当前 migration 对不上

先看：

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

排查顺序：

1. 这套库是不是手工改过表
2. 是不是曾经跑过旧版本结构
3. 是否手工执行过根目录 `sql/schema.sql` 但又继续让 Flyway 接管

建议：

- 本地环境优先重建干净库
- 不要拿长期演化的脏数据卷反复联调 migration

### 12.3 JWT / 登录问题

现象：

- `/api/auth/login` 返回用户名密码错误
- 业务接口 401
- 业务接口 403

先判断是哪一类：

- `login` 失败：先看是否打开了 `demo` profile
- `401`：看 token 是否缺失、过期、secret 是否变了
- `403`：看角色权限是否足够

重点确认：

- `SPRING_PROFILES_ACTIVE` 是否包含 `demo`
- `WORKFLOW_JWT_SECRET` 是否在重启后被改掉
- 请求头是否是 `Authorization: Bearer <token>`

额外注意：

- 当前 demo 用户没有 `operator`
- `editor` 和 `reviewer` 不能覆盖全部动作
- 要跑全链路演示，优先用 `admin`

### 12.4 缓存问题

现象：

- 列表/统计看起来不是最新
- 开了 `redis` profile 后 readiness 失败
- 以为关了两级缓存，实际上还在访问 Redis

先区分当前到底是哪种模式：

- 没开 `redis` profile：纯本地缓存
- 开了 `redis` profile：当前实现会直接走本地 L1 + Redis L2

排查重点：

- `SPRING_PROFILES_ACTIVE` 是否包含 `redis`
- Redis 容器是否 healthy
- 不要只盯 `CACHE_TWO_LEVEL_ENABLED`，当前代码不是靠它切换 CacheManager

### 12.5 任务卡住 / 发布任务不推进

现象：

- 草稿处于 `PUBLISHING`
- `content_publish_task` 里任务停在 `PENDING`、`FAILED` 或 `DEAD`

先看调度模式日志，确认是：

- `LOCAL_FALLBACK`
- `XXL_JOB`
- `HYBRID`
- `NONE`

再看任务数据：

```sql
SELECT task_status, COUNT(*)
FROM content_publish_task
GROUP BY task_status;
```

更细一点可以查：

```sql
SELECT id, draft_id, published_version, task_type, task_status, retry_times, error_message, next_run_at, locked_by, locked_at
FROM content_publish_task
ORDER BY updated_at DESC
LIMIT 20;
```

可用接口：

- `GET /api/workflows/drafts/{draftId}/tasks`
- `POST /api/workflows/drafts/{draftId}/tasks/{taskId}/manual-retry`
- `POST /api/workflows/drafts/{draftId}/tasks/{taskId}/manual-requeue`

### 12.6 Outbox 积压

现象：

- `workflow_outbox_event` 不断累积 `NEW` / `FAILED` / `DEAD`
- RabbitMQ 看不到预期消息

先查：

```sql
SELECT status, COUNT(*)
FROM workflow_outbox_event
GROUP BY status;
```

再看最近事件：

```sql
SELECT id, event_id, event_type, aggregate_type, aggregate_id, status, attempt, next_retry_at, locked_by, error_message
FROM workflow_outbox_event
ORDER BY created_at DESC
LIMIT 20;
```

重点排查：

1. 是否真的开了 `rabbitmq` profile
2. 是否真的设置了 `WORKFLOW_OUTBOX_RELAY_ENABLED=true`
3. RabbitMQ 是否可达
4. 当前是本地调度还是 XXL-Job 调度
5. 事件是否已进入 `DEAD`

如果想恢复失败事件：

- `GET /api/workflows/outbox/events/recovery`
- `POST /api/workflows/outbox/events/{outboxEventId}/manual-retry`
- `POST /api/workflows/outbox/events/{outboxEventId}/manual-requeue`

### 12.7 XXL-Job 问题

现象：

- handler 没注册
- admin 看不到 executor
- 任务重复执行

先确认：

- 是否打开了 `xxl-job` profile
- `XXL_JOB_ADMIN_ADDRESSES` 是否指向 `http://127.0.0.1:8088/xxl-job-admin`
- `xxl_job` 数据库和表是否已准备好
- 是否误把本地调度和 XXL-Job 一起打开

当前最常见坑位：

- compose 暴露端口是 `8088`
- 配置默认地址却是 `8080`

### 12.8 Readiness 失败

现象：

- `/actuator/health/readiness` 是 `DOWN`

先确认你访问的是哪个端口：

- 不开 `ops`：通常是应用端口 `8080`
- 开 `ops`：通常看管理端口 `8081`

然后按 profile 判断：

- 默认只看 `db`
- 开 `redis` 后会额外看 `redis`

所以 readiness 失败时：

1. 先查数据库
2. 如果开了 `redis`，再查 Redis
3. 不要先去怀疑 RabbitMQ 或 XXL-Job，它们不在 readiness 组里

## 13. 常用命令

### 查看依赖状态

```powershell
docker compose -f compose.local.yml ps
```

### 启动最小依赖

```powershell
docker compose -f compose.local.yml up -d mysql
```

### 启动完整联调依赖

```powershell
docker compose -f compose.local.yml up -d mysql redis rabbitmq
```

### 启动 XXL-Job Admin

```powershell
docker compose -f compose.local.yml --profile xxl-job-admin up -d xxl-job-admin
```

### 默认模式启动应用

```powershell
$env:SPRING_PROFILES_ACTIVE=""
mvn spring-boot:run
```

### 完整联调模式启动应用

```powershell
$env:SPRING_PROFILES_ACTIVE="redis,rabbitmq,demo,ops"
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
mvn spring-boot:run
```

### XXL-Job 模式启动应用

```powershell
$env:SPRING_PROFILES_ACTIVE="redis,rabbitmq,demo,ops,xxl-job"
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
$env:XXL_JOB_ADMIN_ADDRESSES="http://127.0.0.1:8088/xxl-job-admin"
mvn spring-boot:run
```

## 14. 一句话总结

这个项目当前最应该记住的运行规律只有四条：

1. 主应用默认就是 MySQL + Flyway，不是 H2。
2. H2 `schema.sql` 主要给测试用，根目录 `sql/*.sql` 主要给手工重建和造数用。
3. `redis` profile 会把缓存切到本地 L1 + Redis L2，`rabbitmq` profile 只启用 outbox 与拓扑，不默认 relay。
4. 默认调度是本地 `@Scheduled`，接 XXL-Job 时一定要关掉本地轮询，并修正本地 admin 地址到 `8088`。

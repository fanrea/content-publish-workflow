# 内容发布工作流服务

这是一个围绕“草稿 -> 审核 -> 发布 -> 回滚 -> 下线 -> 恢复”完整链路构建的后端服务。项目重点不是堆很多 CRUD 接口，而是把真实内容系统里最容易做浅、做散、做不稳的部分落成一个能跑、能讲、能扩展的实现：

- 工作流状态机
- 发布版本快照
- 幂等发布命令
- 异步副作用任务
- Outbox 最终一致性
- JWT 认证与细粒度鉴权
- traceId/requestId 全链路日志
- 人工恢复与审计时间线
- 本地调度与 XXL-Job 调度切换

如果你第一次接手这个仓库，可以先抓住一句话：

> 这是一个把“内容发布”拆成同步主事务、异步任务、可靠消息和恢复运维四条链路的 Spring Boot 项目。

## 1. 这个项目解决什么问题

很多内容系统里的“发布”并不是一次简单的 `UPDATE`，而是下面这些动作的组合：

1. 编辑维护草稿。
2. 草稿进入审核。
3. 审核通过后才能发布。
4. 发布时不仅要保存最终内容，还要触发搜索刷新、读模型同步、通知分发等副作用。
5. 任一步骤失败后，需要支持重试、补偿和人工恢复。
6. 排障时还要能回答“谁发的、发到哪一步、失败在什么环节、现在还能不能恢复”。

这个项目就是把上面这些问题拆清楚，并分别落到：

- HTTP 接口
- 应用服务编排
- 数据库表结构
- 发布任务队列
- Outbox 事件表
- 安全与权限控制
- 运维与审计日志

## 2. 当前真实技术栈

下面这张表是当前工作区代码真实使用的主技术栈，不是旧文档里的历史状态：

| 维度 | 当前实现 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| Web | `spring-boot-starter-web` |
| 安全 | Spring Security + JWT |
| 持久化主路径 | MyBatis-Plus + MyBatis XML |
| 数据库迁移 | Flyway |
| 默认数据库路线 | MySQL |
| 测试 / 嵌入式 DB | H2 |
| 缓存 | Caffeine 本地缓存，可切 Redis，两级缓存已接入 |
| 消息 | RabbitMQ |
| 可靠投递 | Outbox Pattern |
| 调度 | 本地 `@Scheduled` / XXL-Job 二选一或切换 |
| 可观测性 | Actuator + Prometheus |
| API 文档 | Springdoc OpenAPI |

当前 `pom.xml` 可以直接看到这些依赖：

- `spring-boot-starter-security`
- `mybatis-plus-spring-boot3-starter`
- `spring-boot-starter-cache`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-amqp`
- `xxl-job-core`
- `flyway-core`
- `jjwt-*`
- `springdoc-openapi-starter-webmvc-ui`

## 3. 当前已经具备的业务能力

### 3.1 草稿与查询

- 创建草稿
- 修改草稿
- 查询草稿详情
- 草稿分页筛选
- 草稿状态统计
- 草稿工作流摘要

### 3.2 审核链路

- 提交审核
- 审核通过 / 驳回
- 保存审核记录
- 把驳回意见回写到草稿

### 3.3 发布链路

- 发布前 diff 预览
- `Idempotency-Key` 幂等发布
- 生成不可变发布快照
- 根据 diff 规划副作用任务
- 生成发布命令与发布日志
- 草稿进入 `PUBLISHING`

### 3.4 异步副作用

当前内置 3 类发布任务：

- `REFRESH_SEARCH_INDEX`
- `SYNC_DOWNSTREAM_READ_MODEL`
- `SEND_PUBLISH_NOTIFICATION`

任务支持：

- worker 领取
- 自动重试
- 指数退避
- `AWAITING_CONFIRMATION`
- 下游确认成功后最终收口
- 失败转 `FAILED` / `DEAD`

### 3.5 回滚、下线与恢复

- 基于历史快照回滚
- 已发布内容下线
- 查看可恢复发布任务
- 重试单个失败任务
- 批量重试当前版本失败任务
- 查看可恢复 outbox 事件
- 手工重试 / 重排 outbox 事件

### 3.6 审计与时间线

- 发布日志
- 按 `traceId` 查看时间线
- 按 `publishedVersion` 查看聚合后的发布审计时间线
- 记录操作人、状态前后变化、错误码、错误信息、任务 ID、outbox 事件 ID

### 3.7 认证与权限

当前项目已经不是旧版的“只靠请求头伪造角色”路线，而是：

- `/api/auth/login` 登录
- 基于 JWT 的 Bearer Token 认证
- Spring Security 过滤器链接入
- 方法级角色 / 权限注解
- 请求级操作人、角色、权限、审计上下文解析

## 4. 项目整体结构

主代码位于：

```text
src/main/java/com/contentworkflow
```

当前可以先记成下面这张地图：

```text
com.contentworkflow
├─ common
│  ├─ api          统一响应模型
│  ├─ cache        本地缓存、Redis、两级缓存、缓存规格
│  ├─ exception    业务异常
│  ├─ logging      traceId/requestId、MDC、请求日志
│  ├─ messaging    事件、消费、拓扑、Outbox
│  ├─ scheduler    调度模式、XXL-Job、调度追踪
│  ├─ security     JWT、登录、Security 配置
│  └─ web/auth     权限注解、操作人解析、审计上下文
└─ workflow
   ├─ interfaces   Controller、DTO、VO
   ├─ application  应用服务、恢复服务、任务 worker
   ├─ domain       领域实体与枚举
   └─ infrastructure
      ├─ cache
      └─ persistence
         ├─ entity
         ├─ mapper
         └─ mybatis
```

### 分层职责

| 层 | 主要职责 | 典型类 |
| --- | --- | --- |
| `interfaces` | HTTP 入口、参数接收、响应输出 | `ContentWorkflowController` |
| `application` | 业务编排、状态推进、恢复逻辑 | `ContentWorkflowApplicationService` |
| `domain` | 领域对象与状态定义 | `ContentDraft`、`PublishTask` |
| `infrastructure` | 表实体、MyBatis Mapper、持久化细节 | `*Entity`、`*MybatisMapper` |
| `common` | 安全、缓存、消息、日志、调度等基础设施 | `WorkflowSecurityConfig`、`RedisCacheConfig` |

## 5. 当前代码主线，不要再按旧实现理解

这点很重要。旧文档里很多叙事已经落后，尤其是下面两条：

- 不要再把 `InMemoryContentWorkflowService` 当成主应用服务。
- 不要再把 JPA 当成持久化主路径。

当前应该按这条主链路理解项目：

```text
HTTP / JWT
  -> WorkflowTraceLoggingFilter
  -> WorkflowJwtAuthenticationFilter
  -> WorkflowAuthorizationInterceptor
  -> ContentWorkflowController / WorkflowRecoveryController
  -> ContentWorkflowApplicationService / WorkflowRecoveryService
  -> WorkflowStore
  -> MybatisWorkflowStore
  -> MyBatis Mapper + MySQL
```

同步发布完成后，再进入异步链路：

```text
publish / rollback
  -> snapshot + publish_task + publish_command + publish_log
  -> PublishTaskWorker
  -> PublishTaskProgressService
  -> 状态最终收口到 PUBLISHED / PUBLISH_FAILED
```

消息可靠投递链路是：

```text
WorkflowEventPublisher
  -> OutboxWorkflowEventPublisher
  -> workflow_outbox_event
  -> OutboxRelayWorker
  -> RabbitMQ
  -> WorkflowSideEffectConsumerService
  -> PublishTaskProgressService.confirmTaskSuccess(...)
```

## 6. 几个必须建立的心智模型

### 6.1 发布接口返回成功，不等于发布最终完成

发布接口只负责把主事务落稳：

- 改草稿状态
- 创建快照
- 创建发布任务
- 记录命令和日志

真正的“发布完成”由异步任务确认后触发。

### 6.2 快照是不可变版本

- 草稿是当前工作副本
- 快照是某次发布时刻的冻结版本
- 回滚不是覆盖旧数据，而是“拿旧快照重新发布成新版本”

### 6.3 任务状态和草稿状态不是一回事

- 草稿状态描述整体流程阶段
- 发布任务状态描述局部副作用执行进度

### 6.4 失败恢复是正式能力，不是手改数据库

项目内建了恢复接口、恢复日志、恢复校验和版本限制。

### 6.5 traceId / requestId 是贯穿链路的

HTTP 请求、发布任务、Outbox 消息、MQ 消费确认都尽量携带同一套链路标识，便于排障。

## 7. 快速启动

## 7.1 先准备依赖

建议本地直接用仓库根目录的 `compose.local.yml`：

```bash
docker compose -f compose.local.yml up -d
```

默认提供：

- MySQL 8.4
- Redis 7.2
- RabbitMQ 3.13 Management
- 可选的 `xxl-job-admin`

## 7.2 当前默认运行路线

当前默认 `application.yml` 已经切到：

- MySQL 数据源
- Flyway 自动迁移
- Caffeine 本地缓存
- Spring Security + JWT

也就是说：

- 默认不是旧版的 H2 最小模式
- 默认不是 JPA 自动建表
- 默认是更接近真实部署的路线

H2 仍然保留，主要用于测试和嵌入式初始化场景。

## 7.3 本地演示推荐 profile

### 最小开发链路

```bash
set SPRING_PROFILES_ACTIVE=demo
mvn spring-boot:run
```

适合先把登录、工作流接口跑起来。

### 常用联调链路

```bash
set SPRING_PROFILES_ACTIVE=demo,redis,rabbitmq,ops
mvn spring-boot:run
```

### 接入调度中心

```bash
set SPRING_PROFILES_ACTIVE=demo,redis,rabbitmq,ops,xxl-job
mvn spring-boot:run
```

## 7.4 登录方式

如果打开 `demo` profile，会加载 `application-demo.yml` 里的演示账号。

登录接口：

```text
POST /api/auth/login
```

登录成功后拿到 Bearer Token，再访问：

```text
/api/workflows/**
```

## 8. 关键配置与 profile

### `application.yml`

主配置，定义：

- 默认 MySQL + Flyway
- 默认 Caffeine
- JWT 基础配置
- 调度模式偏好
- 请求日志阈值
- Actuator 基础暴露项

### `application-mysql.yml`

显式声明 MySQL / Flyway 路线，便于环境覆盖。

### `application-redis.yml`

定义：

- Redis 连接
- Redis CacheManager
- 两级缓存相关默认参数
- readiness 增加 Redis 检查

### `application-rabbitmq.yml`

定义：

- RabbitMQ 连接参数
- Outbox 开关
- Relay 参数
- 交换机、队列、消费者示例配置

### `application-xxl-job.yml`

定义：

- XXL-Job 执行器
- 关闭本地 `@Scheduled`
- 建议注册的 XXL-Job handler

### `application-demo.yml`

只用于本地演示账号，不建议生产使用。

### `application-example.yml`

给部署或联调用的示例配置。

## 9. 接口概览

基础前缀：

```text
/api/workflows
```

主要接口分组：

- 草稿：`/drafts`、`/drafts/page`、`/drafts/{id}`
- 审核：`/submit-review`、`/review`、`/reviews`
- 发布：`/publish`、`/rollback`、`/publish-diff`
- 观测：`/tasks`、`/commands`、`/logs`、`/logs/timeline`
- 恢复：`/recovery/tasks`、`/manual-retry`、`/manual-requeue`
- outbox 恢复：`/outbox/events/recovery`

更细的接口说明见 `docs/API_DESIGN.md`。

## 10. 数据与基础设施

核心表包括：

- `content_draft`
- `draft_operation_lock`
- `content_review_record`
- `content_publish_snapshot`
- `content_publish_task`
- `content_publish_command`
- `content_publish_log`
- `workflow_outbox_event`

数据库初始化分两条路线：

- MySQL：使用 `src/main/resources/db/migration/mysql/*.sql`
- H2 / 嵌入式：使用 `src/main/resources/sql/schema.sql`

## 11. 适合怎么讲这个项目

如果你需要在面试、课程答辩或团队分享里快速介绍它，可以用下面这段话：

> 我做的是一个内容发布工作流后端，把草稿、审核、发布、回滚、下线、恢复、快照版本、异步副作用、Outbox 最终一致性、JWT 鉴权和审计时间线整合到了同一个系统里。同步接口只负责把主事务可靠落库，异步任务和 Outbox 负责把副作用和消息链路补齐，失败后还能通过正式恢复接口继续推进。

## 12. 文档索引

如果你已经知道自己要找哪一类信息，可以直接看 `docs/` 下这些文档：

- `docs/SOURCE_CODE_GUIDE.md`：按源码结构讲项目主链路
- `docs/OPERATIONS.md`：运行、profile、环境变量、排障
- `docs/API_DESIGN.md`：接口说明
- `docs/SERVICE_ARCHITECTURE.md`：架构分层与组件关系
- `docs/DOMAIN_MODEL.md`：领域模型与状态定义
- `docs/WORKFLOW_INVARIANTS.md`：核心不变量
- `docs/TABLE_DESIGN.md`：表结构设计
- `docs/SQL_GUIDE.md`：SQL 与数据初始化
- `docs/OUTBOX_RABBITMQ.md`：Outbox 与 RabbitMQ
- `docs/REDIS_CACHE.md` / `docs/REDIS_GUIDE.md`：缓存设计与接入
- `docs/OBSERVABILITY.md`：指标、日志、监控
- `docs/TESTING.md`：测试策略

## 13. 推荐的阅读顺序

如果你今天只有 30 分钟，建议按这个顺序看：

1. 先看 `README.md` 建立总图。
2. 再看 `docs/SOURCE_CODE_GUIDE.md` 走源码主链路。
3. 然后看 `ContentWorkflowController` 和 `ContentWorkflowApplicationService`。
4. 再看 `MybatisWorkflowStore`、`PublishTaskWorker`、`PublishTaskProgressService`。
5. 最后看 `WorkflowRecoveryService`、`OutboxRelayWorker`、`WorkflowSecurityConfig`。

这样进入代码，速度会快很多。

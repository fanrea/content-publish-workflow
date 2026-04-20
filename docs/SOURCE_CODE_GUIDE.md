# 源码导读

这份文档面向第一次接手 `content-publish-workflow` 的开发者，目标不是“把所有类逐个念一遍”，而是帮助你尽快建立一张稳定的项目心智地图：

1. 这个系统真正的主线入口在哪。
2. HTTP 请求、发布任务、Outbox、恢复能力分别怎么串起来。
3. 哪些类是“改需求时第一站”，哪些类只是配套设施。
4. 当前代码库里哪些说法已经过时，读源码时不要再被旧认知带偏。

先说结论：当前项目的主线已经不是旧文档里的 `InMemoryContentWorkflowService` + JPA 风格实现。现在真正应该抓住的骨架是：

- 业务编排中枢：`ContentWorkflowApplicationService`
- 正式持久化主线：`WorkflowStore` -> `MybatisWorkflowStore` -> MyBatis Mapper/XML -> MySQL
- 认证与权限：`WorkflowSecurityConfig` -> `WorkflowJwtAuthenticationFilter` -> `WorkflowAuthorizationInterceptor`
- 缓存：`RedisCacheConfig` -> `TwoLevelCacheManager` -> `TwoLevelCache`
- 任务编排：`PublishTaskWorker` + `PublishTaskProgressService`
- 可靠事件投递：`OutboxWorkflowEventPublisher` + `MybatisOutboxEventRepository` + `OutboxRelayWorker`
- 下游消费确认：`WorkflowSideEffectEventLoggingListener` + `WorkflowSideEffectConsumerService`
- 恢复与人工介入：`WorkflowRecoveryService` + `WorkflowReconciliationService`
- 调度切换：本地 `@Scheduled` 与 `XXL-Job` 二选一/可切换
- 观测：`WorkflowTraceLoggingFilter` + `WorkflowLogContext` + MDC 全链路传播

如果你的时间非常有限，先按下面 8 个文件读，能最快建立全局图：

1. `src/main/java/com/contentworkflow/ContentPublishWorkflowApplication.java`
2. `src/main/java/com/contentworkflow/workflow/interfaces/ContentWorkflowController.java`
3. `src/main/java/com/contentworkflow/workflow/application/ContentWorkflowApplicationService.java`
4. `src/main/java/com/contentworkflow/workflow/application/store/WorkflowStore.java`
5. `src/main/java/com/contentworkflow/workflow/application/store/MybatisWorkflowStore.java`
6. `src/main/java/com/contentworkflow/workflow/application/task/PublishTaskWorker.java`
7. `src/main/java/com/contentworkflow/workflow/application/task/PublishTaskProgressService.java`
8. `src/main/java/com/contentworkflow/common/messaging/outbox/OutboxWorkflowEventPublisher.java`

---

## 1. 推荐阅读顺序

### 1.1 第一轮：先建立“系统在跑什么”

推荐顺序：

1. `ContentPublishWorkflowApplication`
2. `ContentWorkflowController`
3. `WorkflowAuthenticationController`
4. `ContentWorkflowApplicationService`
5. `PublishTaskWorker`
6. `PublishTaskProgressService`
7. `OutboxRelayWorker`
8. `WorkflowRecoveryService`

第一轮只回答 4 个问题：

- 一个 HTTP 请求如何进来、走到哪里结束。
- 一次 publish 为什么不会在接口返回时就真正完成。
- 为什么系统里同时有 `content_publish_task` 和 `workflow_outbox_event` 两条异步链。
- 出故障后是自动重试、人工重试，还是直接标记需要介入。

### 1.2 第二轮：再看“状态是怎么落库和受控的”

推荐顺序：

1. `WorkflowStore`
2. `MybatisWorkflowStore`
3. `ContentDraftMybatisMapper.xml`
4. `PublishTaskMybatisMapper.xml`
5. `OutboxEventMybatisMapper.xml`
6. `src/main/resources/sql/schema.sql`
7. `src/main/resources/db/migration/mysql/V1__init_schema.sql`

第二轮重点看：

- `content_draft.lock_version` 怎么做乐观并发控制。
- `draft_operation_lock` 怎么做跨请求、跨线程的操作级互斥。
- `content_publish_command` 怎么做发布命令幂等。
- `content_publish_task` 怎么做任务 claim、退避重试、最终 DEAD。
- `workflow_outbox_event` 怎么做可靠投递和失败补偿。

### 1.3 第三轮：补齐“工程化外壳”

推荐顺序：

1. `WorkflowSecurityConfig`
2. `WorkflowJwtAuthenticationFilter`
3. `WorkflowJwtTokenService`
4. `WorkflowAuthorizationInterceptor`
5. `WorkflowOperatorResolver`
6. `WorkflowPermissionPolicy`
7. `WorkflowTraceLoggingFilter`
8. `WorkflowLogContext`
9. `RedisCacheConfig`
10. `WorkflowSchedulerModeConfiguration`
11. `WorkflowXxlJobHandlers`

这一轮主要回答：

- 登录、JWT、请求上下文、接口权限是怎么挂起来的。
- traceId/requestId 为什么能从 HTTP 一直传到 worker、MQ、scheduler。
- 本地定时轮询和 XXL-Job 调度中心到底谁负责“触发”，谁负责“执行业务”。

---

## 2. 项目分层地图

主代码都在：

```text
src/main/java/com/contentworkflow
```

按职责理解，比按包名死记更重要。

### 2.1 接口层 `workflow.interfaces`

代表类：

- `ContentWorkflowController`
- `WorkflowAuthenticationController`
- `WorkflowRecoveryController`

这一层负责：

- HTTP 路由和参数校验
- 声明权限要求
- 注入当前操作人
- 调用应用服务
- 返回统一 `ApiResponse`

这一层不负责：

- 状态机流转
- 发布任务编排
- 幂等命令判断
- SQL 细节

也就是说，Controller 是“薄入口”，不是业务核心。

### 2.2 应用层 `workflow.application`

代表类：

- `ContentWorkflowApplicationService`
- `PublishTaskWorker`
- `PublishTaskProgressService`
- `WorkflowRecoveryService`
- `WorkflowReconciliationService`
- `OutboxRelayWorker`

这一层是项目真正的主战场。

其中：

- `ContentWorkflowApplicationService` 负责同步业务用例编排
- `PublishTaskWorker` 负责把发布任务从“待执行”推进到“已派发”
- `PublishTaskProgressService` 负责把“下游确认成功”推进成“任务成功/草稿最终发布成功”
- `WorkflowRecoveryService` 负责人工恢复入口
- `WorkflowReconciliationService` 负责扫描 DEAD 任务/事件并生成人工介入信号
- `OutboxRelayWorker` 负责把 outbox 表里的事件真正发到 RabbitMQ

### 2.3 领域层 `workflow.domain`

代表对象：

- `ContentDraft`
- `ContentSnapshot`
- `PublishTask`
- `ReviewRecord`
- `WorkflowStatus`
- `PublishTaskStatus`
- `PublishTaskType`
- `DraftOperationType`

领域层在这个项目里比较“轻”，它更像稳定的数据语义层，而不是重领域模型。

你可以把它理解为：

- 领域对象定义“系统里有哪些核心业务实体”
- 枚举定义“系统允许哪些稳定状态和类型”
- 真正的流程编排仍然主要写在应用服务里

### 2.4 存储层

存储层分两层看：

1. 应用层眼里的抽象接口：`WorkflowStore`
2. 真实实现：`MybatisWorkflowStore`

`WorkflowStore` 暴露给应用层的能力包括：

- 草稿查询与更新
- 审核记录写入
- 快照写入
- 发布任务写入、更新、claim
- 发布命令幂等查询/创建/更新
- 审计日志写入与查询
- 操作锁的获取、续租、释放

`MybatisWorkflowStore` 再往下接：

- `*Entity`
- `*MybatisMapper`
- `src/main/resources/mybatis/*.xml`

这里是当前正式主线，不是 JPA。

### 2.5 公共基础设施层 `common`

按主题看最清楚：

- 安全：`common.security`
- Web 鉴权扩展：`common.web.auth`
- 日志与 trace：`common.logging`
- 缓存：`common.cache`
- 消息与 outbox：`common.messaging`
- 调度与 XXL-Job：`common.scheduler`

这些类不直接实现业务规则，但决定了项目是否像一个“能上线的系统”，而不只是一个 demo。

---

## 3. 先记住这几个核心对象

### 3.1 `ContentDraft`

它是“当前工作副本”。

关键字段：

- `id`：草稿主键
- `version`：乐观锁版本，对应表里的 `lock_version`
- `bizNo`：业务编号
- `draftVersion`：草稿内容版本，编辑会递增
- `publishedVersion`：已发布版本号，发布/回滚后递增
- `status`：工作流状态
- `currentSnapshotId`：当前生效快照
- `lastReviewComment`：最近一次驳回意见

一句话理解：

- `draftVersion` 看“编辑进度”
- `publishedVersion` 看“对外生效进度”
- 两者不是一个概念

### 3.2 `ContentSnapshot`

它是“某次发布时冻结下来的发布视图”。

关键点：

- 发布不是直接把 `ContentDraft` 当最终发布实体
- 系统先固化一个 `ContentSnapshot`
- 下游任务围绕 snapshot 工作
- 回滚也不是“把草稿改回旧值”那么简单，而是基于历史 snapshot 再发一次新的发布流程

### 3.3 `PublishTask`

它代表“某个发布版本下的一项异步副作用”。

当前任务类型：

- `REFRESH_SEARCH_INDEX`
- `SYNC_DOWNSTREAM_READ_MODEL`
- `SEND_PUBLISH_NOTIFICATION`

当前任务状态：

- `PENDING`
- `RUNNING`
- `AWAITING_CONFIRMATION`
- `SUCCESS`
- `FAILED`
- `DEAD`

注意 `AWAITING_CONFIRMATION` 很关键，它说明：

- worker 把任务派发出去了
- 但系统还不认为这项任务最终成功
- 必须等下游消费侧确认，才能进入 `SUCCESS`

### 3.4 `PublishCommandEntry`

它对应 `content_publish_command` 表，是发布命令幂等的锚点。

用途：

- 用 `draftId + commandType + idempotencyKey` 唯一约束防止重复创建发布
- 记录本次命令目标版本、关联 snapshot、失败原因
- 同一个 `Idempotency-Key` 只能重放相同 payload

### 3.5 `PublishLogEntry`

它对应 `content_publish_log` 表，是整个工作流最重要的排障入口之一。

这里会记录：

- 谁发起了什么动作
- 前后状态是什么
- 对应哪个发布版本、任务、outbox 事件
- traceId / requestId 是什么
- 成功、失败、重试、需要人工介入分别发生在什么时候

### 3.6 `OutboxEventEntity`

它对应 `workflow_outbox_event` 表。

它不是“附属日志”，而是可靠消息投递链路的核心实体。

一句话理解：

- 业务代码不是直接 `rabbitTemplate.send(...)`
- 业务代码先写 outbox 表
- 再由 relay worker 异步送 MQ

---

## 4. 目录和主关系图

可以先把系统看成五层：

```text
HTTP / MQ / Scheduler
    ->
接口层（Controller / Listener / XXL Handler）
    ->
应用层（ApplicationService / Worker / RecoveryService）
    ->
存储抽象层（WorkflowStore / OutboxEventRepository）
    ->
MyBatis + DB / RabbitMQ / Cache / Security / Trace
```

更具体一点：

```text
HTTP Request
  -> WorkflowTraceLoggingFilter
  -> Spring Security Filter Chain
  -> WorkflowJwtAuthenticationFilter
  -> DispatcherServlet
  -> WorkflowAuthorizationInterceptor
  -> CurrentWorkflowOperatorArgumentResolver
  -> Controller
  -> ContentWorkflowApplicationService
  -> WorkflowStore
  -> MybatisWorkflowStore
  -> Mapper/XML
  -> MySQL

Publish Async
  -> PublishTaskWorker
  -> PublishTaskHandler
  -> WorkflowEventPublisher
  -> OutboxWorkflowEventPublisher
  -> workflow_outbox_event
  -> OutboxRelayWorker
  -> RabbitMQ
  -> WorkflowSideEffectEventLoggingListener
  -> WorkflowSideEffectConsumerService
  -> PublishTaskProgressService
  -> Draft PUBLISHED / PUBLISH_FAILED
```

---

## 5. HTTP 主链路：从认证到 Controller 到 ApplicationService 到 MyBatis

这是新人最应该先看懂的一条链。

### 5.1 请求进入后，最先经过 `WorkflowTraceLoggingFilter`

类：

- `common.logging.WorkflowTraceLoggingFilter`
- `common.logging.WorkflowLogContext`

它做的事：

- 从请求头读取 `X-Trace-Id` / `X-Request-Id`
- 兼容 `X-B3-TraceId` / `traceparent`
- 没有就自动生成
- 把 traceId/requestId 放进 MDC
- 回写到响应头
- 输出请求摘要日志

所以 trace 不是在 Controller 才出现，而是 HTTP 一进来就落好了。

### 5.2 然后进入 Spring Security 过滤器链

入口配置：

- `WorkflowSecurityConfig`

当前规则：

- `/api/auth/login` 允许匿名访问
- `/api/workflows/**` 必须先认证
- session 是 `STATELESS`
- 不走 formLogin，不走 server-side session

### 5.3 JWT 认证由 `WorkflowJwtAuthenticationFilter` 完成

它的流程很直接：

1. 读取 `Authorization`
2. 校验是否以 `Bearer ` 开头
3. 调 `WorkflowJwtTokenService.authenticate(token)`
4. 解析 operatorId/operatorName/roles
5. 把认证结果放入 `SecurityContext`

`WorkflowJwtTokenService` 负责：

- 校验 issuer、签名、过期时间
- 从 claims 中取出 `operatorId`、`operatorName`、`roles`
- 组装成 `WorkflowJwtPrincipal`

### 5.4 认证之后，还有一层业务语义鉴权

入口：

- `WorkflowAuthorizationInterceptor`

这一步非常重要，因为它说明项目的权限控制分两层：

1. Spring Security 负责“你是不是登录用户”
2. `common.web.auth` 负责“你是不是具备当前工作流角色/权限”

`WorkflowAuthorizationInterceptor` 会：

- 读取 Controller 方法上的 `@RequireWorkflowPermission`
- 读取 Controller 方法上的 `@RequireWorkflowRole`
- 用 `WorkflowOperatorResolver` 从 `SecurityContext` 解出登录身份
- 用 `WorkflowPermissionPolicy` 计算角色权限
- 选出当前请求的有效角色
- 构造 `WorkflowOperatorIdentity`
- 构造 `WorkflowAuditContext(traceId, requestId)`
- 存到 request attribute 和 `WorkflowAuditContextHolder`

因此后面业务层写审计日志时，并不是自己凭空拼 trace/operator，而是这里先把上下文打好了。

### 5.5 Controller 层只做“薄转发”

例如 `ContentWorkflowController.publish(...)`：

- 从 Header 读取 `Idempotency-Key`
- 与请求体里的 `idempotencyKey` 做归一
- 注入 `@CurrentWorkflowOperator WorkflowOperatorIdentity`
- 直接调用 `contentWorkflowService.publish(...)`

真正的发布编排不在 Controller，而在应用服务。

### 5.6 应用层主入口是 `ContentWorkflowApplicationService`

这里是同步业务编排总枢纽。

HTTP 主链路最后会在这里做：

- 读取草稿
- 校验状态
- 计算 diff
- 创建 snapshot
- 创建 publish command
- 创建 publish tasks
- 写 publish log
- 切换草稿状态

### 5.7 再往下统一落到 `WorkflowStore`

设计目的很明确：

- 应用层不直接依赖具体 ORM
- 存储能力被抽象成 `WorkflowStore`
- 当前正式实现是 `MybatisWorkflowStore`

### 5.8 `MybatisWorkflowStore` 才是当前正式持久化主线

这层做 3 类事：

1. 把领域对象映射成 `*Entity`
2. 调 MyBatis Mapper
3. 在关键查询上接缓存注解

几个非常重要的实现细节：

- `findDraftById`、`listDrafts`、`countDraftsByStatus` 带缓存
- `updateDraft` 走条件更新，不是无脑覆盖
- `tryAcquireDraftOperationLock` 通过插入锁行/替换过期锁来抢占操作锁
- `claimRunnablePublishTasks` 负责把任务从可运行状态改成 `RUNNING`

### 5.9 最底层是 Mapper/XML 和数据库

最值得看的 SQL：

- `ContentDraftMybatisMapper.xml`
  - `conditionalUpdate` 用 `id + lock_version + expectedStatuses + biz_no` 做条件更新
- `PublishTaskMybatisMapper.xml`
  - `selectRunnableForUpdate` 只 claim `PENDING/FAILED` 且到期且锁过期的任务
- `OutboxEventMybatisMapper.xml`
  - `selectClaimCandidates` 只 claim `NEW/FAILED` 且到期且锁过期的 outbox 事件

这一层直接体现出系统的并发控制策略，不要跳过。

---

## 6. 登录、JWT、请求上下文、权限拦截主链路

这部分是当前代码新增的重要主线，旧文档通常讲得不够完整。

### 6.1 登录链路

入口：

- `POST /api/auth/login`
- `WorkflowAuthenticationController`
- `WorkflowLoginService`

流程：

```text
POST /api/auth/login
  -> WorkflowAuthenticationController.login
  -> WorkflowLoginService.login
  -> AuthenticationManager
  -> WorkflowDemoAccountAuthenticationProvider
  -> WorkflowJwtTokenService.createToken
  -> 返回 accessToken / expiresAt / operator 信息
```

当前默认认证提供者是 `WorkflowDemoAccountAuthenticationProvider`。

它依赖：

- `WorkflowDemoAccountService`
- `workflow.security.login.demo-users`

也就是说：

- 主配置默认没有内置账号
- `application-demo.yml` 才提供 demo 用户
- 本地演示账号是 profile 驱动的，不是硬编码主线

### 6.2 请求上下文如何传到业务层

在业务层最终会出现两个重要上下文：

- `WorkflowOperatorIdentity`
- `WorkflowAuditContext`

来源分别是：

- `WorkflowOperatorIdentity`：认证 + 角色权限解析后得到
- `WorkflowAuditContext`：拦截器里根据 request/trace 生成

Controller 方法里通过：

- `@CurrentWorkflowOperator`

拿到业务操作人。

应用层写日志时通过：

- `WorkflowAuditContextHolder.get()`

拿到 traceId / requestId。

### 6.3 权限模型不是“角色判断写死在 Controller if 里”

权限相关类：

- `WorkflowPermission`
- `WorkflowPermissionPolicy`
- `WorkflowOperatorResolver`
- `WorkflowAuthorizationInterceptor`

角色：

- `EDITOR`
- `REVIEWER`
- `OPERATOR`
- `ADMIN`

权限是聚合出来的，不是散落在业务方法里手写判断。

例如：

- `EDITOR` 能写草稿、提交审核、查看发布 diff
- `REVIEWER` 能审核
- `OPERATOR` 能执行 publish/rollback/offline，查看任务/命令/日志，做任务手工重试
- `ADMIN` 拥有全部权限，还能人工处理 outbox

因此如果你要改权限，不要先去 Controller 改，先看 `WorkflowPermissionPolicy`。

---

## 7. 发布 diff、幂等命令、快照、发布任务、任务确认、最终收口

这是项目最核心的主业务流程。

### 7.1 `getPublishDiff` 是发布前的“变更规划器”

入口：

- `ContentWorkflowApplicationService.getPublishDiff`
- `loadPublishDiff(...)`

它做的事不是简单字段比较，而是：

- 选择基准发布版本
- 加载基准 snapshot
- 生成字段 diff
- 分析正文是否只是格式变化、内容变化、结构变化
- 归纳为 `PublishChangeScope`
- 反推出本次计划生成哪些 `PublishTask`

当前规划规则大意：

- 首次发布：三个任务都建
- 只改 metadata：索引刷新 + 读模型同步 + 通知
- 改正文内容或结构：索引刷新 + 读模型同步
- 纯格式变化：通常只需要读模型同步

这一步很重要，因为 publish 不是无脑固定创建三类任务，而是按 diff 决定。

### 7.2 `publish(...)` 的真实主流程

源码主入口：

- `ContentWorkflowApplicationService.publish(Long draftId, PublishRequest request, WorkflowOperatorIdentity operator)`

建议按下面顺序理解：

```text
1. 读取 draft
2. 归一化 idempotencyKey
3. 计算 publish diff
4. 如存在同 key 命令，先走幂等判断
5. 校验 draft 状态只能是 APPROVED / PUBLISH_FAILED
6. 校验必须存在可发布变化
7. 计算 targetPublishedVersion = currentPublishedVersion + 1
8. 获取 draft_operation_lock
9. 写入 content_publish_command(IN_PROGRESS)
10. draft 状态切到 PUBLISHING
11. 创建新 snapshot
12. 按 diff 创建 publish tasks
13. 更新 draft.publishedVersion / currentSnapshotId
14. publish command 改成 ACCEPTED
15. 写入 PUBLISH_REQUESTED 审计日志
16. 返回当前 draft
```

这里最容易误解的点有 4 个。

#### 误区 1：`publish()` 返回就代表发布完成

不是。

`publish()` 返回时，系统只完成了“发布意图落库 + 异步任务建好 + 草稿进入 `PUBLISHING`”。

真正的完成条件是：

- 同一 `publishedVersion` 下的所有 `PublishTask`
- 都被下游确认成 `SUCCESS`
- `PublishTaskProgressService.tryFinalizeDraft(...)`
- 把草稿从 `PUBLISHING` 切到 `PUBLISHED`

#### 误区 2：幂等只是防重复提交，不校验 payload

也不是。

`publish()` 会通过 `content_publish_command` 做幂等，同时还会调用：

- `ensureIdempotencyKeyNotReusedForDifferentPayload(...)`

逻辑是：

- 如果同一个 `Idempotency-Key` 之前已经对应过某个 snapshot
- 当前 draft 内容和那个 snapshot 不一致
- 就抛 `IDEMPOTENCY_KEY_REUSED`

这能防止“拿旧 key 发新内容”。

#### 误区 3：snapshot 只是审计备份

不对。

snapshot 是发布主流程的一部分：

- 它承载“本次发布版本”的冻结内容
- 异步任务围绕 snapshot 工作
- 回滚也基于历史 snapshot 重新组织发布

#### 误区 4：publish task 是固定模板

不是。

任务集合来自 diff 规划，不同发布内容可能创建不同任务组合。

### 7.3 `rollback(...)` 本质是“基于旧 snapshot 再发布一次”

入口：

- `ContentWorkflowApplicationService.rollback(...)`

流程不是“把库字段改回旧值”，而是：

1. 校验当前状态必须是 `PUBLISHED` 或 `OFFLINE`
2. 找到目标历史 snapshot
3. 获取新的操作锁
4. draft 状态切到 `PUBLISHING`
5. 基于历史 snapshot 内容创建一个新的 rollback snapshot
6. 为新的 `publishedVersion` 创建全部 publish tasks
7. 更新 draft 当前内容为目标 snapshot 内容
8. 写 `ROLLBACK_REQUESTED` 审计日志
9. 后续仍然走发布任务异步链路收口

所以：

- rollback 复用 publish 的任务执行框架
- 它不是数据库层面的“回退”
- 它是业务层面的“重新发布到历史版本内容”

### 7.4 `offline(...)` 更轻，但仍受锁和状态约束

入口：

- `ContentWorkflowApplicationService.offline(...)`

它要求：

- 当前只能从 `PUBLISHED` 下线
- 先获取 `draft_operation_lock`
- 再把状态切到 `OFFLINE`
- 写审计日志

它不像 publish/rollback 那样创建 snapshot 和任务，但仍然受统一并发控制模型保护。

---

## 8. 发布任务异步链：从任务创建到最终确认

这是很多人第一次读源码时最容易断掉的一段。

### 8.1 任务是在哪里被创建的

在 `ContentWorkflowApplicationService.publish/rollback` 中。

创建完成后落到：

- `content_publish_task`

初始状态：

- `PENDING`

### 8.2 谁来领取任务

入口：

- `PublishTaskWorker.pollOnce()`

触发方式有两种：

- 本地模式：`@Scheduled`
- XXL-Job 模式：`WorkflowXxlJobHandlers.workflowPublishTaskPollJob`

真正干活的是 `PublishTaskWorker`，不是调度器本身。

### 8.3 `PublishTaskWorker` 做了什么

核心步骤：

```text
pollOnce
  -> claimRunnablePublishTasks
  -> 对每个 task 恢复 trace 上下文
  -> 加载对应 draft 和 snapshot
  -> 找到 PublishTaskHandler
  -> 执行 handler
  -> 成功后 markTaskDispatched
  -> 失败后 FAILED 或 DEAD
  -> DEAD 时尝试把 draft 推到 PUBLISH_FAILED 并做补偿
```

关键点：

- task claim 依赖 `PublishTaskMybatisMapper.xml` 里的 `select ... for update`
- 只会领取 `PENDING/FAILED` 且可执行且锁超时的任务
- worker 会把 task 状态推进到 `RUNNING`
- handler 本身通常不直接调下游，而是发布事件

### 8.4 `DefaultPublishTaskHandlers` 只是“发事件的适配层”

当前默认 handler 有三类：

- 搜索索引刷新
- 下游读模型同步
- 发布通知

这几个 handler 的共同点：

- 自己不把业务副作用写死在 handler 里
- 而是把 `PublishTaskContext` 组装成 `WorkflowEvent`
- 再交给 `WorkflowEventPublisher`

所以 handler 更像“任务 -> 事件”的转换器。

### 8.5 为什么任务成功后先到 `AWAITING_CONFIRMATION`

入口：

- `PublishTaskProgressService.markTaskDispatched(...)`

原因很重要：

- worker 只能证明“消息已派发”
- 不能证明“下游已经处理完成”

所以任务状态设计成两段：

1. worker 派发成功 -> `AWAITING_CONFIRMATION`
2. consumer 确认成功 -> `SUCCESS`

这是项目避免“消息刚发出去就误判业务成功”的关键设计。

### 8.6 下游消费成功后，谁来确认任务

链路：

```text
RabbitMQ Consumer
  -> WorkflowSideEffectEventLoggingListener
  -> WorkflowSideEffectConsumerService
  -> 对应 Gateway
  -> PublishTaskProgressService.confirmTaskSuccess(...)
```

`WorkflowSideEffectConsumerService` 会：

- 调实际网关
- 记录消费日志
- 调 `confirmTaskSuccess(...)`

### 8.7 谁负责最终把草稿收口为 `PUBLISHED`

不是 worker。

真正收口的是：

- `PublishTaskProgressService.tryFinalizeDraft(...)`

它会检查：

- 当前 draft 是否仍是目标 `publishedVersion`
- 当前 draft 是否仍在 `PUBLISHING`
- 同版本下是否存在 `DEAD` 任务
- 同版本下是否全部 `SUCCESS`

只有全部满足，才：

- 把 draft 改成 `PUBLISHED`
- 释放 `draft_operation_lock`
- 写 `PUBLISH_COMPLETED` 日志
- 发布 `CONTENT_PUBLISHED` 业务事件

这一步才是“发布真的完成了”。

---

## 9. Outbox 事件投递与消费确认链路

项目里有两条异步链，必须分清：

1. `content_publish_task`：业务副作用任务链
2. `workflow_outbox_event`：可靠消息投递链

第二条不是第一条的重复实现，而是给第一条和其他业务事件提供可靠投递底座。

### 9.1 为什么不是直接发 RabbitMQ

因为系统要保证：

- 业务状态先安全落库
- 事件投递失败可恢复
- 事件发送有重试、死信、人工介入能力

所以统一采用 outbox 模式。

### 9.2 事件发布入口：`WorkflowEventPublisher`

这是一个抽象接口。

当前配置类：

- `WorkflowMessagingConfiguration`

行为：

- `workflow.outbox.enabled=true` 时，注入 `OutboxWorkflowEventPublisher`
- 否则注入 `NoopWorkflowEventPublisher`

也就是说，代码里依赖的是发布抽象，是否真正启用 outbox 由配置决定。

### 9.3 `OutboxWorkflowEventPublisher` 做的不是发 MQ，而是写表

它会：

1. 接收 `WorkflowEvent`
2. 通过 `WorkflowMessagingTraceContext.enrichOutboundHeaders(...)` 补齐 trace/request headers
3. 解析 routing key
4. 序列化 payload / headers
5. 写 `workflow_outbox_event`

表字段会保存：

- `eventId`
- `eventType`
- `aggregateType`
- `aggregateId`
- `aggregateVersion`
- `exchangeName`
- `routingKey`
- `payloadJson`
- `headersJson`
- `traceId`
- `requestId`
- `status`
- `attempt`

### 9.4 `MybatisOutboxEventRepository` 负责把 trace 字段与 headers 同步

这是一个容易忽略但很关键的点。

它做两件工程化工作：

- hydrate：如果 traceId/requestId 字段为空，尝试从 headersJson 补回
- synchronize：保存前把 traceId/requestId 同步进 headersJson

因此：

- outbox 表结构里有显式 trace/request 字段，便于 SQL 排障
- 同时消息头里也保留完整上下文，便于 MQ 侧恢复 MDC

### 9.5 `OutboxRelayWorker` 才负责真正发 RabbitMQ

它的流程：

```text
pollOnce
  -> claimBatch(NEW/FAILED)
  -> status = SENDING
  -> 恢复 trace 上下文
  -> 转 AMQP Message
  -> rabbitTemplate.send(exchange, routingKey, msg)
  -> 成功则 SENT
  -> 失败则 FAILED / DEAD
```

关键细节：

- claim 也走 `select ... for update`
- 只处理 `NEW/FAILED`
- 发送前先把事件状态改成 `SENDING`
- 只有持有租约的 worker 才能把事件最终改成 `SENT`
- 失败会指数退避，超过最大重试数进 `DEAD`

### 9.6 RabbitMQ 侧的消费确认链

消费者入口：

- `WorkflowSideEffectEventLoggingListener`

它做的事：

- 从 AMQP headers 恢复 trace 上下文
- 用 `WorkflowMessageDeduplicationGuard` 以 `messageId` 去重
- 反序列化 payload
- 调 `WorkflowSideEffectConsumerService`

消费确认的真正业务意义是：

- 不是“MQ 收到消息了”
- 而是“下游副作用完成了，因此可以把对应 publish task 标为 SUCCESS”

### 9.7 RabbitMQ 拓扑在哪里定义

配置类：

- `WorkflowRabbitTopologyConfiguration`

当前定义：

- 一个 Topic Exchange
- 三个 queue
- 三个 routing key 绑定

对应三类副作用任务：

- 搜索索引刷新
- 读模型同步
- 发布通知

---

## 10. 恢复能力、操作锁、并发控制

这一块是项目工程质量最强的部分之一。

### 10.1 并发控制不是只靠一种机制

当前至少有 4 层保护：

1. 草稿乐观锁：`content_draft.lock_version`
2. 草稿操作锁：`draft_operation_lock`
3. 发布命令幂等：`content_publish_command`
4. 任务/事件 claim 锁：`locked_by + locked_at + for update`

这四层分别解决不同问题。

### 10.2 乐观锁：防止同一草稿被并发改写

入口：

- `MybatisWorkflowStore.updateDraft(...)`
- `ContentDraftMybatisMapper.xml` 的 `conditionalUpdate`

条件更新约束：

- `id`
- `lock_version`
- `biz_no`
- `expectedStatuses`

所以应用层不仅要求“版本没变”，还要求“状态仍是预期状态”。

这保证了：

- 不能在别的请求已经推进状态后继续写旧对象
- 也不能越过状态机边界乱写

### 10.3 操作锁：防止 publish/rollback/offline/恢复互相打架

入口：

- `ContentWorkflowApplicationService.acquireDraftOperationLock(...)`
- `WorkflowRecoveryService.ensureDraftOperationLock(...)`

表：

- `draft_operation_lock`

它保护的是“流程级互斥”，不是字段级并发。

典型场景：

- 一个 draft 正在 publish
- 另一个请求又来 rollback
- 或者人工恢复线程要重试任务

这时系统不是靠“碰碰运气”，而是直接拒绝并给出当前锁信息。

锁字段里还会记录：

- `operationType`
- `targetPublishedVersion`
- `lockedBy`
- `expiresAt`

所以排障时能看出是谁占了锁、锁到什么时候。

### 10.4 幂等命令：防重复创建发布流程

入口：

- `ContentWorkflowApplicationService.publish(...)`

表：

- `content_publish_command`

它解决的不是“编辑冲突”，而是：

- 同一个发布请求因为网络重试、网关重试、客户端重试被重复提交

当前命令状态：

- `IN_PROGRESS`
- `ACCEPTED`
- `FAILED`

语义：

- `IN_PROGRESS`：命令已被抢占，正在建立发布流程
- `ACCEPTED`：命令已完成落库，异步任务待后续收口
- `FAILED`：命令本身在建流程阶段失败

### 10.5 任务 claim：防止多个 worker 同时执行一条任务

入口：

- `MybatisWorkflowStore.claimRunnablePublishTasks(...)`
- `PublishTaskMybatisMapper.xml`

筛选条件：

- 状态是 `PENDING` 或 `FAILED`
- `nextRunAt <= now`
- `locked_at` 为空或已过期

被 claim 后会：

- 状态改成 `RUNNING`
- 写入 `lockedBy`
- 写入 `lockedAt`

### 10.6 Outbox claim：防止多个 relay worker 重复发同一事件

入口：

- `OutboxRelayWorker.claimBatch(...)`
- `OutboxEventMybatisMapper.xml`

逻辑与任务 claim 类似，只是对象换成了 outbox event。

### 10.7 恢复能力不是“故障后临时补丁”，而是正式设计的一部分

恢复相关入口：

- `WorkflowRecoveryController`
- `WorkflowRecoveryService`
- `WorkflowReconciliationService`

支持的能力：

- 查询某个 draft 下可恢复的 publish tasks
- 手工重试单个任务
- 手工重试当前版本全部失败/死亡任务
- 查询可恢复的 outbox 事件
- 手工重试 outbox 事件
- 扫描 DEAD 任务/事件并生成人工介入信号

### 10.8 手工恢复不是随便把状态改回去

例如 `retryPublishTaskInternal(...)` 会做：

1. 校验任务状态必须是 `FAILED/DEAD`
2. 校验任务版本必须是当前 draft 的 `publishedVersion`
3. 确保存在有效 `draft_operation_lock`
4. 把 task 改回 `PENDING`
5. 清空错误和锁
6. 必要时把 draft 从 `PUBLISH_FAILED` 恢复到 `PUBLISHING`
7. 写审计日志

这意味着恢复接口仍然遵守状态机，而不是暴力改表。

### 10.9 `WorkflowReconciliationService` 负责“发现需要人工介入”

它不直接替你恢复，而是：

- 扫描 `DEAD` publish task
- 扫描 `DEAD` outbox event
- 追加 intervention 日志
- 用缓存标记避免重复刷相同告警

这块通常用于运维巡检、定时扫描和后台看板。

---

## 11. Trace / requestId / MDC 传播链

如果你想排查“为什么日志串不起来”，这部分必须看透。

### 11.1 中心类是 `WorkflowLogContext`

它负责：

- 解析 header
- 生成 fallback id
- 兼容 `traceparent` / `B3`
- 写入 MDC
- 从 map/header 中恢复上下文
- 给异步任务包装上下文

这不是一个“辅助工具类”，而是本项目 trace 语义的中心。

### 11.2 HTTP 链路中的传播

```text
HTTP Header
  -> WorkflowTraceLoggingFilter
  -> MDC(traceId, requestId)
  -> WorkflowAuthorizationInterceptor
  -> WorkflowAuditContextHolder
  -> ApplicationService
  -> PublishLog / PublishTask / OutboxEvent
```

### 11.3 publish 相关日志有一条固定的发布 trace

`WorkflowAuditLogFactory.publishTraceId(draftId, publishedVersion)` 会生成：

```text
publish:{draftId}:{publishedVersion}
```

这意味着：

- 同一次发布版本下的 publish log、task log、outbox log 可以按一个稳定 traceId 聚合
- `getPublishAuditTimeline(...)` 就是基于这个 trace 组织时间线

### 11.4 任务和事件会把 trace/request 持久化

持久化位置：

- `content_publish_task.trace_id / request_id`
- `workflow_outbox_event.trace_id / request_id`
- `content_publish_log.trace_id / request_id`

好处：

- 即使异步线程、MQ 消费、甚至应用重启后继续执行
- 仍能恢复原始链路信息

### 11.5 MQ 传播

链路：

```text
Application/Worker
  -> WorkflowMessagingTraceContext.enrichOutboundHeaders
  -> Outbox headersJson
  -> OutboxRelayWorker 转成 AMQP Header
  -> WorkflowSideEffectEventLoggingListener.openInboundScope
  -> 消费侧 MDC 恢复
```

### 11.6 Scheduler 传播

相关类：

- `WorkflowSchedulerTraceContext`
- `TracingThreadPoolTaskScheduler`
- `TracingXxlJobHandler`
- `TracingXxlJobSpringExecutor`

作用：

- 给本地 `@Scheduled` 线程补 trace/request 上下文
- 给 XXL-Job handler 包一层 trace
- 在 scheduler 场景下往 MDC 里额外写入触发来源信息

所以调度线程不是“裸线程”，日志里同样能看到链路标识。

---

## 12. 调度链路：本地 `@Scheduled` 与 XXL-Job 模式切换

当前项目把“执行逻辑”和“触发方式”分得很清楚。

### 12.1 真正执行业务的是 worker，不是调度框架

真正业务 worker：

- `PublishTaskWorker`
- `OutboxRelayWorker`
- `WorkflowReconciliationService`

调度器只是决定“何时调用它们”。

### 12.2 本地模式

触发点：

- `PublishTaskWorker.scheduledPollOnce()`
- `OutboxRelayWorker.scheduledPollOnce()`

条件：

- `workflow.scheduler.local.enabled=true`

适合：

- 单机开发
- 联调
- 冒烟测试

### 12.3 XXL-Job 模式

入口：

- `WorkflowXxlJobHandlers`

当前 job：

- `workflowPublishTaskPollJob`
- `workflowOutboxRelayJob`
- `workflowDeadPublishTaskScanJob`
- `workflowDeadOutboxScanJob`

条件：

- `xxl.job.executor.enabled=true`

并且 `application-xxl-job.yml` 会显式关闭：

- `workflow.scheduler.local.enabled=false`

这样可以避免本地轮询和 XXL-Job 双触发同一 worker。

### 12.4 启动时会报告当前调度模式

类：

- `WorkflowSchedulerModeConfiguration`

启动日志会明确告诉你当前是：

- `LOCAL`
- `XXL_JOB`
- `HYBRID`
- `NONE`

这对排查“为什么 worker 没跑”非常有用。

---

## 13. 缓存主线：当前真实用法和应该怎样理解

旧文档常常把缓存讲成“有 Redis 就行”，当前实现其实更细。

### 13.1 缓存配置中心

入口：

- `RedisCacheConfig`

行为：

- 默认 `spring.cache.type=caffeine`
- 只启本地缓存
- 如果启用 redis profile 且存在 `RedisConnectionFactory`
- 则组装 `TwoLevelCacheManager(local + redis)`

### 13.2 两级缓存怎么工作的

核心类：

- `TwoLevelCacheManager`
- `TwoLevelCache`

读取顺序：

```text
先查 L1(local)
  -> miss 再查 L2(redis)
  -> L2 命中则回填 L1
  -> 都 miss 再走 loader
```

写入顺序：

- 先远端，再本地

失效：

- evict 两层一起失效

### 13.3 当前主要缓存哪些读路径

在 `MybatisWorkflowStore` 上最明显：

- `findDraftById`
- `listDrafts`
- `countDraftsByStatus`

也就是说缓存离“真实查询边界”很近，还是以 Store 为中心。

### 13.4 还有两个工程性缓存

缓存名见 `CacheNames`：

- `CONSUMED_WORKFLOW_MESSAGE`
  - 用于 MQ 消费去重
- `DEAD_OUTBOX_SCAN_MARKER`
  - 用于 DEAD outbox 扫描去重，避免重复打相同 intervention 信号

### 13.5 `DraftCacheFacade` 的定位

这个类存在，但当前并不是主业务流程的核心入口。

它更像：

- 面向未来扩展的缓存访问门面
- 统一封装某些按草稿读取/失效的缓存操作

读主流程时，不要把它当成理解业务的第一站。

---

## 14. 关键表：新人必须有表级心智模型

最重要的 8 张表：

### 14.1 `content_draft`

存当前草稿主数据。

你可以把它当成“工作流聚合根”。

### 14.2 `draft_operation_lock`

存当前草稿正在进行的重操作锁。

它不是数据库事务锁，而是跨请求、跨线程、可观察、可过期的流程锁。

### 14.3 `content_review_record`

存审核历史。

审核动作和草稿状态流转是同时推进的。

### 14.4 `content_publish_snapshot`

存每个已发起发布版本的冻结内容。

回滚也会新增 snapshot。

### 14.5 `content_publish_task`

存发布副作用任务。

这是发布链的异步执行中心。

### 14.6 `content_publish_command`

存幂等发布命令。

这是接口级幂等和重复提交控制中心。

### 14.7 `content_publish_log`

存审计日志、任务日志、outbox 日志、人工介入日志。

这是排障和时间线查询中心。

### 14.8 `workflow_outbox_event`

存可靠消息投递事件。

这是系统事件可靠输出中心。

---

## 15. 如果我要改某类功能，先看哪里

这一节是给新人最快定位用的。

### 15.1 我要改接口字段、接口行为、参数校验

先看：

1. `workflow/interfaces/*Controller.java`
2. `workflow/interfaces/dto/*`
3. `workflow/interfaces/vo/*`
4. `ContentWorkflowApplicationService`

### 15.2 我要改草稿编辑、审核流转、状态机

先看：

1. `ContentWorkflowApplicationService`
2. `WorkflowStatus`
3. `WorkflowStore`
4. `MybatisWorkflowStore`

重点关注：

- `ensureState(...)`
- `persistDraft(...)`
- `updateDraft(... expectedStatuses ...)`

### 15.3 我要改发布前 diff 规则

先看：

1. `ContentWorkflowApplicationService.getPublishDiff`
2. `loadPublishDiff(...)`
3. `analyzeBody(...)`
4. `buildChangeScopes(...)`
5. `buildPlannedTasks(...)`

### 15.4 我要改 publish / rollback 主流程

先看：

1. `ContentWorkflowApplicationService.publish`
2. `ContentWorkflowApplicationService.rollback`
3. `WorkflowAuditLogFactory`
4. `PublishCommandEntry`
5. `DraftOperationType`

### 15.5 我要新增一种发布副作用任务

先看：

1. `PublishTaskType`
2. `DefaultPublishTaskHandlers`
3. `PublishTaskEventFactory`
4. `WorkflowSideEffectEventLoggingListener`
5. `WorkflowSideEffectConsumerService`
6. 对应 gateway 接口

通常要同时改 4 个面：

- 新任务类型枚举
- publish diff 规划规则
- handler 发事件
- consumer 收事件并确认任务

### 15.6 我要改任务重试、失败策略、最终 DEAD 规则

先看：

1. `PublishTaskWorker.markFailedOrDead`
2. `PublishTaskProgressService`
3. `WorkflowRecoveryService`
4. `WorkflowReconciliationService`

### 15.7 我要改 Outbox/RabbitMQ

先看：

1. `WorkflowMessagingConfiguration`
2. `OutboxWorkflowEventPublisher`
3. `MybatisOutboxEventRepository`
4. `OutboxRelayWorker`
5. `WorkflowRabbitTopologyConfiguration`
6. `WorkflowSideEffectEventLoggingListener`

### 15.8 我要改人工恢复/运维排障接口

先看：

1. `WorkflowRecoveryController`
2. `WorkflowRecoveryService`
3. `WorkflowReconciliationService`
4. `content_publish_log`

### 15.9 我要改登录/JWT/权限

先看：

1. `WorkflowAuthenticationController`
2. `WorkflowLoginService`
3. `WorkflowDemoAccountAuthenticationProvider`
4. `WorkflowJwtTokenService`
5. `WorkflowSecurityConfig`
6. `WorkflowAuthorizationInterceptor`
7. `WorkflowPermissionPolicy`

### 15.10 我要改 trace、日志串联、MDC

先看：

1. `WorkflowTraceLoggingFilter`
2. `WorkflowLogContext`
3. `WorkflowAuditLogFactory`
4. `WorkflowMessagingTraceContext`
5. `WorkflowSchedulerTraceContext`

### 15.11 我要改缓存

先看：

1. `RedisCacheConfig`
2. `TwoLevelCacheManager`
3. `TwoLevelCache`
4. `MybatisWorkflowStore`
5. `CacheNames`

### 15.12 我要改调度方式

先看：

1. `WorkflowSchedulerModeConfiguration`
2. `WorkflowXxlJobHandlers`
3. `PublishTaskWorker.scheduledPollOnce`
4. `OutboxRelayWorker.scheduledPollOnce`
5. `application-xxl-job.yml`

---

## 16. 新人最容易踩的几个误区

### 16.1 不要再把 `InMemoryWorkflowStore` 当成主线

它仍然存在，但不是当前正式落地路径。

理解系统时应默认站在：

- `WorkflowStore`
- `MybatisWorkflowStore`
- MyBatis XML
- MySQL

这条线上。

### 16.2 不要再按 JPA 风格去猜测持久化行为

当前关键写路径大量依赖：

- 条件更新
- 手写 Mapper
- XML SQL
- 显式 claim

很多语义不是“保存对象就自动生效”，而是“只有满足 expectedStatuses/expectedVersion 才能更新成功”。

### 16.3 不要把 publish task 和 outbox event 混为一谈

两者关系是：

- publish task：业务副作用编排单元
- outbox event：可靠消息投递单元

publish task 经常会借助 outbox 发消息，但它们不是同一个抽象。

### 16.4 不要把“消息发出”当成“业务完成”

当前系统明确区分：

- 派发成功
- 下游确认成功
- 全部任务成功
- 草稿最终发布完成

如果你读源码时没区分这四层，就会误判很多状态。

### 16.5 不要忽略 `content_publish_log`

线上排障时，它往往比单纯看 application log 更有用，因为：

- 它带业务语义
- 它能串 draft/task/outbox/version
- 它能用固定 publish traceId 聚合整次发布

---

## 17. 建议的阅读姿势

如果你准备真正改功能，建议按下面的方法读代码：

### 17.1 先找入口，再找状态推进点

例如你要理解发布：

1. 从 `ContentWorkflowController.publish` 开始
2. 进 `ContentWorkflowApplicationService.publish`
3. 找状态变化：
   - `APPROVED -> PUBLISHING`
   - `PUBLISHING -> PUBLISHED`
   - `PUBLISHING -> PUBLISH_FAILED`
4. 再看哪些类触发这些变化

### 17.2 再找数据落点

看：

- 哪张表新增记录
- 哪张表更新状态
- 哪个日志动作写入了 `content_publish_log`

### 17.3 最后再补工程化链条

即：

- trace 怎么传
- 权限怎么拦
- 调度怎么触发
- 缓存怎么影响读取

这样读，心智模型最稳。

---

## 18. 读完这份导读后，你应该形成的结论

如果这份文档起作用，你应该已经明确下面这些判断：

- 业务主中枢是 `ContentWorkflowApplicationService`，不是旧的内存实现。
- 正式持久化主线是 `WorkflowStore` -> `MybatisWorkflowStore` -> MyBatis XML -> MySQL。
- `publish()` 返回时只是进入 `PUBLISHING`，最终完成要等任务确认链路收口。
- 发布前有 diff 规划，不是每次都固定跑同一组任务。
- 幂等不只防重复提交，还防相同 key 对应不同 payload。
- snapshot 是发布主模型的一部分，不是单纯审计备份。
- Outbox 是可靠消息底座，不是“顺手记一条日志”。
- 系统同时使用乐观锁、操作锁、幂等命令、claim 锁做多层并发保护。
- JWT 认证之后还有一层工作流语义权限拦截。
- traceId/requestId 会从 HTTP 进入 MDC，再进入任务、日志、outbox、MQ、scheduler。
- 调度器只是触发器，真正业务逻辑仍在 worker/service。
- 恢复能力是正式设计的一部分，不是线上坏了才想到的人肉补救。

---

## 19. 延伸阅读建议

如果你已经读完这份文档，下一步建议配合这些主题继续深挖：

- 表结构与索引：`src/main/resources/sql/schema.sql`
- 发布流程与日志：`ContentWorkflowApplicationService` + `WorkflowAuditLogFactory`
- 任务执行：`PublishTaskWorker` + `PublishTaskProgressService`
- 消息链路：`OutboxWorkflowEventPublisher` + `OutboxRelayWorker` + `WorkflowSideEffectEventLoggingListener`
- 权限链路：`WorkflowSecurityConfig` + `WorkflowJwtAuthenticationFilter` + `WorkflowAuthorizationInterceptor`
- 调度链路：`WorkflowSchedulerModeConfiguration` + `WorkflowXxlJobHandlers`

这份文档的职责是帮你快速建立主干理解。真正开始改需求时，优先围绕“入口类 + 状态推进点 + 落库表 + 审计日志”四件事来读源码，效率最高。

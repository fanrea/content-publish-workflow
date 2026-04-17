# 服务架构说明

这份文档不是接口清单，也不是数据库字段罗列，而是专门回答下面这些“第一次接手项目时最容易问出来”的问题：

1. 这个项目到底是一个什么服务，边界在哪里？
2. `common`、`workflow.interfaces`、`workflow.application`、`workflow.domain`、`workflow.infrastructure` 分别负责什么？
3. 一次“发布”为什么不会在接口里立刻全部完成？
4. 为什么项目里既有草稿状态，又有任务状态、命令记录、快照和 outbox 事件？

如果你想沿着源码一步一步跟踪一条请求怎么执行，建议再配合阅读 [SOURCE_CODE_GUIDE.md](./SOURCE_CODE_GUIDE.md)。

## 一、先用一句话理解项目

这个项目是一个“内容发布工作流后端服务”。

它做的事情不是“保存一篇文章”这么简单，而是把下面这条完整业务链路做成可运行、可恢复、可审计的系统：

```text
编辑草稿
  -> 提交审核
  -> 审核通过
  -> 发起发布
  -> 触发搜索刷新 / 读模型同步 / 通知分发
  -> 记录审计日志与事件
  -> 失败后支持自动重试和人工恢复
```

这意味着它关心的不只是“数据存下来没有”，还关心：

- 状态是不是允许这么流转
- 发布是否重复提交
- 历史线上版本能不能回溯
- 副作用失败后能不能补救
- 运维能不能看懂一条链路出了什么问题

## 二、这个服务不负责什么

理解系统边界很重要。这个项目目前主要负责工作流本身，不负责下面这些外围系统：

- 页面渲染和前端界面
- 完整的用户账号体系
- 真正的搜索系统实现
- 真正的下游读模型系统实现
- 真正的消息通知平台实现

项目里虽然有 `SearchIndexRefreshGateway`、`ReadModelSyncGateway`、`PublishNotificationGateway` 这些类，但它们更像“对外适配口”。当前仓库重点是把工作流骨架搭好，而不是把所有外部系统都做完。

可以把这个服务理解成“发布中台里的工作流引擎”。

## 三、整体分层长什么样

项目主代码在 `src/main/java/com/contentworkflow` 下，大致可以分成两大块：

1. `common`
2. `workflow`

其中 `workflow` 再按典型分层架构拆成四层。

### 1. `common`：全项目共享的基础设施

`common` 不是具体业务，而是支撑业务运行的公共能力。新人第一次看代码时，经常会误以为这里是“杂物间”，其实这里放的是横切关注点。

主要子目录职责如下：

- `common.api`
  作用：统一接口返回格式。
  典型类：`ApiResponse`、`PageResponse`。
  你可以把它理解成“Controller 返回值的统一包装层”。

- `common.cache`
  作用：缓存配置、缓存名称和键规则。
  典型类：`CacheNames`、`CacheKeys`、`RedisCacheConfig`。
  这里解决的是“哪些查询值得缓存、启用 Redis 时怎么接入”的问题。

- `common.exception`
  作用：统一业务异常模型。
  典型类：`BusinessException`。
  业务代码抛的不是随意的 `RuntimeException`，而是带业务错误码的异常。

- `common.web`
  作用：Web 层通用处理。
  典型类：`GlobalExceptionHandler`。
  这里负责把异常翻译成前端能看懂的 HTTP 响应。

- `common.web.auth`
  作用：轻量权限模型、操作人解析、审计上下文注入。
  典型类：`WorkflowAuthorizationInterceptor`、`WorkflowPermissionPolicy`、`CurrentWorkflowOperatorArgumentResolver`。
  它解决的是“请求是谁发的、带什么角色、有没有权限、traceId/requestId 怎么贯穿全链路”。

- `common.messaging`
  作用：消息事件、RabbitMQ 配置、消费防重、下游网关、消费者处理。
  典型类：`WorkflowEvent`、`WorkflowEventPublisher`、`WorkflowSideEffectConsumerService`。
  这里把“工作流系统”和“消息系统”之间的连接方式定义出来。

- `common.messaging.outbox`
  作用：Outbox 模式实现。
  典型类：`OutboxEventEntity`、`OutboxWorkflowEventPublisher`、`JpaOutboxEnqueuer`。
  它解决的是“数据库提交成功但 MQ 没发出去”这种典型一致性问题。

- `common.scheduler`
  作用：XXL-Job 相关配置。
  这让本地轮询模式和调度中心模式可以共存。

一句话总结：`common` 放的是“所有业务都可能用到，但不属于某个单一业务流程本身”的能力。

### 2. `workflow.interfaces`：接口层

这一层直接面对 HTTP 请求，是系统的“门口”。

它主要负责：

- 定义 REST API
- 做参数接收和校验
- 绑定权限注解
- 注入当前操作人
- 调用应用服务
- 把结果封装成统一响应

这层最核心的类有：

- `ContentWorkflowController`
- `WorkflowRecoveryController`
- `dto/*`
- `vo/*`

可以把它理解成“翻译层”：

- 把 HTTP 请求翻译成 Java 请求对象
- 把应用层返回结果翻译成前端响应对象

它**不应该**负责：

- 编排复杂业务流程
- 直接操作数据库
- 自己维护状态机

如果你在 Controller 里看到大量 `if-else` 判断业务状态，通常就意味着分层开始变差。这个项目总体上比较克制，绝大多数编排都放在应用层。

### 3. `workflow.application`：应用层

这是项目最值得重点阅读的一层，也是“业务编排中枢”。

它主要负责：

- 协调草稿、审核、发布、回滚、下线流程
- 生成快照、任务、命令、日志
- 管理失败重试与人工恢复
- 调用消息发布器
- 推进状态从“请求已受理”走到“最终完成”

这一层的关键类可以先记住：

- `InMemoryContentWorkflowService`
  作用：主业务服务，绝大多数业务编排都在这里。

- `PublishTaskWorker`
  作用：后台 worker，负责领取发布任务并触发副作用。

- `PublishTaskProgressService`
  作用：发布任务进度协调器，负责把“任务已发出”“下游已确认”“草稿可转为已发布”这些动作串起来。

- `OutboxRelayWorker`
  作用：把 outbox 表里的事件异步投递到 RabbitMQ。

- `WorkflowRecoveryService`
  作用：人工恢复失败任务和 outbox 事件。

- `WorkflowReconciliationService`
  作用：对账、扫描异常状态，为恢复和运维提供辅助能力。

- `WorkflowXxlJobHandlers`
  作用：把本地 worker 能力接入 XXL-Job 调度。

应用层可以理解成“导演”：

- 它自己通常不直接干底层存储细活
- 也不是纯领域对象定义层
- 它负责安排“谁先做、谁后做、失败怎么办”

### 4. `workflow.domain`：领域层

这一层是业务语言层，定义“系统里到底有哪些核心概念”。

它主要放：

- 领域实体
- 工作流状态
- 审核决策
- 任务类型
- 任务状态
- 角色与审计枚举

这里最重要的价值是：给业务一个稳定、清晰、统一的词汇表。

比如：

- `ContentDraft` 代表当前工作副本
- `ContentSnapshot` 代表某个历史发布版本
- `PublishTask` 代表发布副作用任务
- `WorkflowStatus` 代表草稿总体状态
- `PublishTaskStatus` 代表单个副作用任务状态

对于新人来说，一个常见误区是把“领域层”理解成“只放 POJO 的地方”。其实不是。它真正的意义是把业务概念讲清楚，让后面的代码讨论有共同语言。

### 5. `workflow.infrastructure`：基础设施实现层

这一层是“把领域对象真的存起来”的地方。

它主要放：

- JPA Entity
- Repository
- Entity 和 Domain 之间的映射器
- 缓存门面

常见子目录：

- `workflow.infrastructure.persistence.entity`
- `workflow.infrastructure.persistence.repository`
- `workflow.infrastructure.persistence.mapper`
- `workflow.infrastructure.cache`

这一层的特点是“技术细节更重”：

- 表字段怎么映射
- 查询怎么分页
- 条件更新怎么做
- 唯一索引冲突怎么处理

它服务于上层，但不应该反过来决定业务流程。

## 四、为什么发布要拆成两段

很多新人第一次看这个项目时，会问：

“为什么 `publish` 接口不直接把所有事做完？为什么还要任务表、worker、确认服务？”

答案是：真实生产里的发布往往不是一个本地原子动作，而是一串有副作用的动作。

比如一次发布往往要做这些事：

- 保存最终快照
- 刷新搜索索引
- 同步下游读模型
- 发送通知消息
- 记录审计日志

其中前一项主要是本地数据库写入，后几项通常都依赖外部系统。

如果把这些事全部塞进同一个 HTTP 请求里，会遇到几个典型问题：

- 接口很慢，用户体验差
- 任何一个外部系统抖动都可能导致发布整体失败
- 很难判断“主业务成功了，但副作用只完成了一半”这种中间状态
- 很难做重试、恢复和审计

所以项目把发布拆成两段：

### 1. 同步主事务阶段

这段在 `InMemoryContentWorkflowService.publish(...)` 里完成，重点是“可靠落库”。

它主要做：

- 校验状态是否合法
- 校验幂等键
- 获取草稿级操作锁
- 生成发布快照
- 生成发布任务
- 记录发布命令
- 写入审计日志
- 必要时写入 outbox 事件
- 把草稿推进到 `PUBLISHING`

可以理解成：先把“发布这件事已经正式开始”这个事实可靠地写进系统。

### 2. 异步副作用阶段

这段主要由 `PublishTaskWorker`、`PublishTaskProgressService`、`WorkflowSideEffectConsumerService` 协作完成。

它主要做：

- worker 扫描可执行任务
- 领取任务并加锁
- 调用对应 handler 触发副作用
- 任务进入 `AWAITING_CONFIRMATION`
- 下游消费成功后回调确认
- 任务转成 `SUCCESS`
- 当前版本全部任务成功后，草稿从 `PUBLISHING` 推进到 `PUBLISHED`

这一设计很关键，因为它明确区分了三种不同含义：

- 请求已接收
- 副作用已分发
- 整个发布真正完成

## 五、当前项目里几条最重要的运行链路

### 1. 正常请求链路

```text
HTTP 请求
  -> Web 拦截器鉴权
  -> Controller
  -> Application Service
  -> WorkflowStore
  -> JPA/DB
  -> 返回统一响应
```

这条链路主要覆盖：草稿创建、编辑、审核、查询等同步动作。

### 2. 发布任务链路

```text
publish / rollback
  -> Application Service 生成任务
  -> content_publish_task 入库
  -> PublishTaskWorker 领取任务
  -> PublishTaskHandler 执行
  -> PublishTaskProgressService 标记 AWAITING_CONFIRMATION
  -> 下游消费成功后 confirmTaskSuccess(...)
  -> 所有任务 SUCCESS
  -> 草稿变为 PUBLISHED
```

注意这里有一个容易忽略的点：

`PublishTaskWorker` 现在并不是“执行完 handler 就立刻把任务改成 SUCCESS”，而是先进入 `AWAITING_CONFIRMATION`。这意味着当前项目已经不是最早那种“本地模拟成功即完成”的模式，而是支持“分发成功”和“下游确认成功”两个阶段。

### 3. Outbox 链路

```text
业务事件
  -> OutboxWorkflowEventPublisher
  -> workflow_outbox_event
  -> OutboxRelayWorker 领取
  -> RabbitMQ
  -> 消费者
  -> 去重保护
  -> 下游网关 / 任务确认
```

这条链路解决的是“业务数据”和“消息发送”之间的最终一致性问题。

### 4. 人工恢复链路

```text
查询 recoverable tasks/events
  -> WorkflowRecoveryService
  -> 校验只允许恢复当前版本
  -> 必要时重新获取草稿操作锁
  -> FAILED/DEAD -> PENDING 或 NEW
  -> 写审计日志
  -> worker 重新执行
```

这条链路让系统在失败后仍然可运营，而不是只能手改数据库。

## 六、为什么系统里会同时出现这么多“状态”

新人最容易混淆的一点是：为什么状态这么多？

这里其实有三类状态，不能混为一谈。

### 1. 草稿状态 `WorkflowStatus`

它描述“这篇内容整体处于哪个业务阶段”。

典型值：

- `DRAFT`
- `REVIEWING`
- `APPROVED`
- `PUBLISHING`
- `PUBLISHED`
- `PUBLISH_FAILED`
- `OFFLINE`

它回答的是“大盘状态”。

### 2. 任务状态 `PublishTaskStatus`

它描述“某个副作用任务执行到哪一步了”。

当前代码中的状态包括：

- `PENDING`：等 worker 领取
- `RUNNING`：worker 正在处理
- `AWAITING_CONFIRMATION`：请求已经发给下游，等下游确认
- `SUCCESS`：下游确认成功
- `FAILED`：失败但可自动重试
- `DEAD`：自动重试耗尽，需要人工介入

它回答的是“局部执行进度”。

### 3. Outbox 状态 `OutboxEventStatus`

它描述“消息投递动作本身的状态”。

典型值：

- `NEW`
- `SENDING`
- `SENT`
- `FAILED`
- `DEAD`

它回答的是“消息有没有成功发出去”。

把这三类状态拆开，系统才能精确表达真实情况。比如：

- 草稿已经 `PUBLISHING`
- 任务 1 `SUCCESS`
- 任务 2 `AWAITING_CONFIRMATION`
- outbox 事件还在 `FAILED`

这在真实系统里是很正常的，不是设计过度。

## 七、草稿级操作锁为什么存在

当前项目新引入了 `draft_operation_lock` 和 `DraftOperationLockEntry`，这是理解现有架构时必须注意的一点。

它解决的问题是：

“同一篇草稿能不能同时发起两个互斥操作？”

答案通常是不行。比如下面这些动作本质上互斥：

- 发布
- 回滚
- 下线
- 对当前版本失败任务做人工恢复

如果没有锁，可能出现这些问题：

- 同一篇草稿被重复发布
- 回滚和发布同时发生
- 恢复任务时状态又被别的请求改掉
- 最终版本号和日志链路对不上

所以项目在草稿级别引入了租约锁，当前支持的操作类型有：

- `PUBLISH`
- `ROLLBACK`
- `OFFLINE`

它的特点是：

- 不是永久锁，而是带过期时间的租约
- 成功链路会释放
- 异常情况下即使没释放，过期后也能被重新接管
- 恢复流程也会尝试复用或重新获取这个锁

可以把它理解成“同一篇草稿在同一时刻只允许一个关键动作当导演”。

## 八、为什么这个结构适合继续扩展

从工程角度看，这个架构最有价值的地方，不是“层很多”，而是它把容易耦合的东西拆开了。

已经拆开的核心边界包括：

- 接口接收 和 业务编排拆开
- 当前草稿 和 历史快照拆开
- 主事务 和 异步副作用拆开
- 任务执行 和 下游确认拆开
- 自动重试 和 人工恢复拆开
- 业务日志 和 技术运行日志拆开

这样后续扩展时，改动通常可以落在相对明确的位置：

- 要新增接口：优先看 `workflow.interfaces`
- 要新增流程编排：优先看 `workflow.application`
- 要新增任务类型：看 `PublishTaskType`、`PublishTaskHandler`、`DefaultPublishTaskHandlers`
- 要改存储实现：看 `WorkflowStore`、`JpaWorkflowStore`
- 要接入真实消息链路：看 `common.messaging` 和 `common.messaging.outbox`
- 要增强恢复和运维能力：看 `WorkflowRecoveryService`、`WorkflowReconciliationService`

## 九、给第一次接手项目的同学一个阅读建议

如果你现在还是觉得“概念很多”，可以按这个顺序看：

1. 先看 `ContentWorkflowController`，知道系统提供了哪些入口。
2. 再看 `InMemoryContentWorkflowService`，理解同步主流程怎样编排。
3. 接着看 `PublishTaskWorker` 和 `PublishTaskProgressService`，理解为什么发布不是接口返回就结束。
4. 然后看 `WorkflowSideEffectConsumerService`，理解下游确认如何回推任务成功。
5. 再看 `WorkflowStore` 和 `JpaWorkflowStore`，理解这些业务对象最终怎么落库。
6. 最后看 `WorkflowRecoveryService` 和 `OutboxRelayWorker`，理解失败后怎么恢复。

只要把这条阅读路径走通，这个项目的大部分结构就不会再显得零散。

# 项目逻辑与源码导读

这份文档专门给第一次接手项目的人看。

目标不是把所有类都讲一遍，而是帮助你快速建立下面三件事的心智模型：

1. 代码目录怎么分层，每层到底负责什么。
2. 一次请求会依次经过哪些关键类。
3. 发布、回滚、恢复、消息投递这些“看起来很散”的逻辑，源码里到底是怎样串起来的。

如果你刚入组，建议把这份文档当成“源码地图”。看代码时不要一上来就全局搜索所有类名，而是先按这里给的顺序走一遍。

## 一、先看目录，而不是先看实现细节

主代码都在：

```text
src/main/java/com/contentworkflow
```

目录结构可以先粗略记成下面这样：

```text
com.contentworkflow
├─ common
│  ├─ api
│  ├─ cache
│  ├─ exception
│  ├─ messaging
│  │  └─ outbox
│  ├─ scheduler
│  └─ web
│     └─ auth
└─ workflow
   ├─ interfaces
   │  ├─ dto
   │  └─ vo
   ├─ application
   │  ├─ store
   │  └─ task
   ├─ domain
   │  ├─ entity
   │  └─ enums
   └─ infrastructure
      ├─ cache
      └─ persistence
         ├─ entity
         ├─ mapper
         └─ repository
```

第一次看时，不要被目录多吓到。可以先这样理解：

- `common`：所有业务都可能会用到的公共基础设施
- `workflow.interfaces`：HTTP 入口
- `workflow.application`：业务编排核心
- `workflow.domain`：业务概念定义
- `workflow.infrastructure`：数据库和缓存落地实现

## 二、建议的阅读顺序

如果你时间有限，就按下面这个顺序读：

1. `workflow.interfaces.ContentWorkflowController`
2. `workflow.application.InMemoryContentWorkflowService`
3. `workflow.application.task.PublishTaskWorker`
4. `workflow.application.task.PublishTaskProgressService`
5. `common.messaging.WorkflowSideEffectConsumerService`
6. `workflow.application.task.WorkflowRecoveryService`
7. `workflow.application.store.WorkflowStore`
8. `workflow.application.store.JpaWorkflowStore`
9. `common.web.auth.WorkflowAuthorizationInterceptor`
10. `workflow.application.task.OutboxRelayWorker`

这个顺序背后的思路是：

- 先看系统对外暴露什么能力
- 再看同步主流程怎么编排
- 再看异步链路怎么补全
- 最后再看底层怎么落库和投递消息

## 三、先建立一个“请求怎么走”的总图

你可以先把项目想象成四条主线同时存在：

1. HTTP 主链路
2. 发布任务链路
3. Outbox 消息链路
4. 失败恢复链路

对应的源码流向大概如下：

```text
浏览器 / 前端 / 调用方
  -> Controller
  -> Application Service
  -> WorkflowStore
  -> JPA Repository / DB

publish / rollback
  -> 创建 PublishTask
  -> PublishTaskWorker
  -> PublishTaskHandler
  -> PublishTaskProgressService
  -> WorkflowSideEffectConsumerService
  -> 草稿最终转为 PUBLISHED

业务事件
  -> OutboxWorkflowEventPublisher
  -> workflow_outbox_event
  -> OutboxRelayWorker
  -> RabbitMQ

失败任务 / 失败事件
  -> WorkflowRecoveryService
  -> 重新排队
  -> worker 继续执行
```

## 四、`workflow.interfaces`：接口层怎么看

这一层最重要的类是：

- `ContentWorkflowController`
- `WorkflowRecoveryController`

先不要纠结每个注解，先看方法名。你会立刻发现项目提供的业务入口很完整：

- 草稿创建、修改、查询、分页、统计
- 提交审核、审核通过/驳回
- 发布、回滚、下线
- 查看快照、任务、命令、日志、时间线
- 手工重试失败任务和 outbox 事件

### 1. 接口层真正负责什么

接口层的职责很克制，主要就是：

- 收请求
- 校验参数
- 限制权限
- 注入当前操作人
- 调应用服务
- 返回统一响应

比如 `publish(...)` 方法里一个值得注意的细节是：

- 请求体里可以传 `idempotencyKey`
- 请求头里也可以传 `Idempotency-Key`
- Controller 会先把它们归一化，再交给服务层

这说明接口层允许做“轻量适配”和“参数收口”，但不会在这里做复杂流程编排。

### 2. DTO 和 VO 分别看什么

`workflow.interfaces.dto` 里的类是“请求对象”。

你可以把它理解成：调用方允许提交什么字段、字段格式有什么限制。

比如：

- `CreateDraftRequest`
- `UpdateDraftRequest`
- `PublishRequest`
- `RollbackRequest`
- `ManualRecoveryRequest`

`workflow.interfaces.vo` 里的类是“响应对象”。

它们回答的是：应用层结果最终要以什么结构返回给前端或调用方。

比如：

- `ContentDraftResponse`
- `PublishTaskResponse`
- `PublishDiffResponse`
- `PublishAuditTimelineResponse`

新人常见误区是把 DTO/VO 当成“纯传输壳子，不重要”。其实它们能反映系统对外暴露了什么能力，读一遍很有助于建立整体印象。

## 五、`common.web.auth`：请求在进业务前先经过谁

在 Controller 真正执行前，请求会先经过权限和审计上下文这一层。

这里最值得看的类有：

- `WorkflowAuthorizationInterceptor`
- `WorkflowPermissionPolicy`
- `WorkflowOperatorResolver`
- `CurrentWorkflowOperatorArgumentResolver`

### 1. 这层在做什么

它主要完成几件事：

- 从请求头里解析角色和操作人
- 检查方法上的权限注解
- 选择当前生效角色
- 生成 `WorkflowAuditContext`
- 把当前操作人对象注入到 Controller 参数

这层的价值是把权限和审计入口统一起来，避免每个接口方法都手写：

```java
if (!hasPermission(...)) { ... }
```

### 2. 为什么它对理解业务很重要

因为项目里的很多日志都会带上：

- `operatorId`
- `operatorName`
- `requestId`
- `traceId`

这些信息不是在应用服务里“凭空出现”的，而是从 Web 层入口一路传下去的。

## 六、`InMemoryContentWorkflowService`：主业务编排中枢

虽然类名里有 `InMemory`，但它现在不是一个“只跑内存 demo 的服务类”，而是整个项目最核心的业务编排实现。

### 1. 先理解它负责什么

这个类基本负责了所有同步业务动作的编排：

- 创建草稿
- 修改草稿
- 提交审核
- 审核通过 / 驳回
- 预览发布 diff
- 发起发布
- 发起回滚
- 下线
- 查询草稿、快照、任务、命令、日志、时间线

你可以把它理解成“工作流应用服务总入口”。

### 2. 这个类里有哪几类方法

读这个类时，可以把方法按四组分开看：

#### 第一组：普通同步动作

比如：

- `createDraft`
- `updateDraft`
- `submitReview`
- `review`
- `offline`

这些动作特点是：

- 主要影响草稿本身状态
- 不需要创建异步副作用任务
- 事务边界比较直观

#### 第二组：发布编排动作

比如：

- `getPublishDiff`
- `publish`
- `rollback`

这组是最核心也最复杂的，因为它们会影响：

- 草稿状态
- 发布版本号
- 快照
- 发布任务
- 发布命令
- 审计日志
- 草稿级操作锁

#### 第三组：查询聚合动作

比如：

- `pageDraftSummaries`
- `getDraftWorkflowSummary`
- `listSnapshots`
- `listPublishTasks`
- `listPublishLogs`
- `getPublishAuditTimeline`

这些方法的特点是“查出来的不只是单张表数据，而是面向页面和排障场景的聚合结果”。

#### 第四组：辅助私有方法

比如：

- `requireDraft`
- `ensureState`
- `persistDraft`
- `acquireDraftOperationLock`
- `releaseDraftOperationLockQuietly`
- `loadPublishDiff`

这些方法对新人很关键，因为它们能告诉你这个类内部真正依赖了哪些约束和不变量。

## 七、草稿、审核和编辑链路怎么读

这部分建议把 `createDraft`、`updateDraft`、`submitReview`、`review` 放在一起看。

### 1. `createDraft`

核心作用很简单：

- 创建一个 `ContentDraft`
- 初始状态设为 `DRAFT`
- 初始化 `draftVersion=1`
- 初始化 `publishedVersion=0`
- 落库
- 写一条 `DRAFT_CREATED` 审计日志

这一步定下的是“当前工作副本”的初始语义。

### 2. `updateDraft`

重点不是“改字段”，而是“重新回到草稿态”。

方法里最值得注意的点：

- 只有 `DRAFT`、`REJECTED`、`OFFLINE` 能编辑
- 编辑后 `draftVersion + 1`
- 状态重置为 `DRAFT`
- 清空最近一次驳回意见

这反映出一个业务语义：只要再次编辑，草稿就进入新的编辑版本，不继续沿用旧驳回语义。

### 3. `submitReview`

它做的事情很直接：

- 校验当前状态
- 草稿状态改为 `REVIEWING`
- 写日志

### 4. `review`

这个方法要特别注意“两条线同时保存”：

- 一条线是当前草稿状态怎么变
- 另一条线是审核历史怎么记录

所以它既会：

- 插入 `ReviewRecord`

也会：

- 更新 `ContentDraft.status`

这就是为什么系统既能告诉你“现在这篇稿子是什么状态”，也能告诉你“它经历过哪些审核决策”。

## 八、发布前差异计算：为什么会有 `getPublishDiff`

很多同学第一次看会觉得：

“发布不就是发吗，为什么还要先做 diff？”

当前项目里，diff 的作用不是为了页面好看，而是为了**决定这次到底需要创建哪些副作用任务**。

重点方法是：

- `getPublishDiff(...)`
- `loadPublishDiff(...)`

它会做这些事情：

- 找出基线发布版本
- 找到对应快照
- 对标题、摘要、正文分别做字段差异比较
- 判断正文变化是内容变动、结构变动还是纯格式变动
- 汇总影响范围
- 计算应该创建哪些任务类型

可以把这部分理解成“发布决策器”：

- 它不直接发布
- 但它决定这次发布的副作用计划

## 九、`publish`：最核心的方法到底做了什么

建议你第一次读 `publish` 时不要从头到尾顺着看，而是先按三个主题拆开。

### 1. 主题一：幂等保护

这部分解决的是：

“用户重复点击发布，会不会重复生成快照和任务？”

关键机制：

- 读取并规范化 `idempotencyKey`
- 查询 `content_publish_command`
- 同 key 已存在时校验是否是同一个请求
- 已存在且状态合适时直接复用，不重复建任务

这不是单纯为了“接口返回一样的结果”，而是为了避免重复副作用。

### 2. 主题二：草稿级操作锁

在真正开始发布前，会调用：

- `acquireDraftOperationLock(...)`

这一步很重要。它保证同一篇草稿在某个关键时刻不会同时做互斥动作，比如：

- 一个线程正在发布
- 另一个线程又想回滚
- 或者恢复流程同时介入

锁对象是：

- `DraftOperationLockEntry`

对应的操作类型是：

- `PUBLISH`
- `ROLLBACK`
- `OFFLINE`

注意这里的锁是“带过期时间的租约锁”，不是永久锁。

### 3. 主题三：主事务编排

发布进入主事务后，核心动作依次是：

1. 把草稿推进到 `PUBLISHING`
2. 创建新的 `ContentSnapshot`
3. 根据 diff 计算任务类型
4. 批量创建 `PublishTask`
5. 更新草稿的 `publishedVersion` 和 `currentSnapshotId`
6. 更新命令状态
7. 写 `PUBLISH_REQUESTED` 日志

要注意一个核心事实：

`publish(...)` 返回成功，并不等于“发布已经最终完成”。

它只意味着：

- 发布主事务已成功落地
- 后续副作用已经排队

草稿要真正进入 `PUBLISHED`，还要等后面的异步链路全部完成。

## 十、`rollback`：为什么不是“把旧数据覆盖回来”

新人常见误解：

“回滚是不是把草稿改回旧内容就行？”

当前实现不是这样。

`rollback(...)` 的真实语义是：

- 找到历史快照
- 基于历史快照内容生成一个新的发布版本
- 新版本继续走发布任务链路

这意味着：

- 回滚本质上仍是一种发布
- 只是发布内容来源于旧快照

这样设计的好处是：

- 历史版本不会被覆盖
- 时间线是连续可追踪的
- 下游系统收到的是一次完整的新版本发布

源码里你会看到：

- 新建 `rollbackSnapshot`
- 标记 `rollback=true`
- 为该版本重新创建任务
- 草稿回到 `PUBLISHING`

## 十一、`offline`：为什么简单，但仍然有锁

`offline(...)` 看起来最简单，只是把 `PUBLISHED` 改成 `OFFLINE`。

但当前代码仍然会先获取草稿级操作锁。

原因很现实：

- 下线也是互斥操作
- 它不能和发布、回滚并发进行

这说明锁的存在不是只服务于复杂流程，而是服务于“互斥业务动作的串行化”。

## 十二、`WorkflowStore`：应用层到底依赖了什么存储能力

读到这里，建议去看 `workflow.application.store.WorkflowStore`。

这个接口很重要，因为它定义了“应用层认为存储层应该提供哪些能力”。

### 1. 不要把它只看成 CRUD 接口

它不仅有：

- `insertDraft`
- `findDraftById`
- `updateDraft`

还有很多带业务语义的方法：

- `pageDrafts`
- `countDraftsByStatus`
- `claimRunnablePublishTasks`
- `tryCreatePublishCommand`
- `findDraftOperationLock`
- `tryAcquireDraftOperationLock`
- `renewDraftOperationLock`
- `releaseDraftOperationLock`
- `listPublishLogsByTraceId`

这说明存储层抽象不是机械 CRUD，而是“围绕业务动作暴露能力”。

### 2. 为什么这对项目很重要

因为应用层依赖的不是某种具体数据库实现，而是这组业务能力。

所以如果未来要替换底层实现，优先保证的是这些能力语义不变，而不是方法名不变。

## 十三、`JpaWorkflowStore`：数据库细节都藏在哪里

接着看 `JpaWorkflowStore`，你会发现很多“表面简单、实际上很关键”的实现细节都在这里。

### 1. 条件更新而不是盲目覆盖

比如更新草稿时，会结合：

- 当前 `lock_version`
- 允许的旧状态集合

一起做条件更新。

这样做是为了避免：

- 旧请求覆盖新状态
- 并发请求把状态机冲乱

### 2. 锁获取和锁续租都在这里落地

草稿级操作锁并不是内存锁，而是数据库里的租约锁。

这里对应的方法有：

- `tryAcquireDraftOperationLock(...)`
- `renewDraftOperationLock(...)`
- `releaseDraftOperationLock(...)`

这说明锁不仅服务于单机，也服务于多实例场景。

### 3. 任务领取是“查出来再处理”吗

不是简单的 `select * where status = PENDING`。

任务领取还要考虑：

- 状态是否可领取
- `nextRunAt` 是否到期
- 锁是否超时
- 领取后如何立刻标成运行中

这部分逻辑封在 `claimRunnablePublishTasks(...)` 里。

## 十四、`PublishTaskWorker`：发布任务为什么不是建完就算成功

这部分是理解异步链路的关键。

### 1. `pollOnce`

入口方法主要做三件事：

1. 批量领取可执行任务
2. 逐个执行任务
3. 对等待确认的任务续租草稿操作锁

最后这一点是当前代码里很重要的新变化：任务分发后不一定立即结束，所以锁需要在“等待下游确认”的阶段持续续租。

### 2. `executeOne`

读这个方法时可以关注几个检查点：

- 任务必须是 `RUNNING`
- 草稿必须还存在
- 当前发布版本对应的快照必须存在
- 必须能找到对应 `PublishTaskHandler`

如果这些前置条件不成立，任务会直接变成不可恢复失败或进入失败处理分支。

### 3. 为什么 handler 执行成功后不是直接 `SUCCESS`

这是当前项目理解难度最高、也最有工程价值的一点。

在新版本实现里：

- handler 执行成功
- 任务并不会马上标记 `SUCCESS`
- 而是交给 `PublishTaskProgressService.markTaskDispatched(...)`
- 任务状态变成 `AWAITING_CONFIRMATION`

这说明系统承认一个现实：

“我把请求发给下游了”不等于“下游已经真正完成了”。

## 十五、`PublishTaskProgressService`：真正推进“发布完成”的类

如果说 `PublishTaskWorker` 负责“把任务发出去”，那 `PublishTaskProgressService` 就负责“把任务做完这件事在系统里落稳”。

这个类建议重点看三个方法。

### 1. `markTaskDispatched`

作用：

- 任务从 `RUNNING` 进入 `AWAITING_CONFIRMATION`
- 清理错误和锁字段
- 续租草稿级操作锁
- 写 `TASK_DISPATCHED` 审计日志

你可以把它理解成：

“系统已经把副作用请求发出去了，接下来等下游回执。”

### 2. `confirmTaskSuccess`

这个方法通常由消息消费侧成功回调触发。

它会：

- 校验任务身份是否匹配
- 把任务改成 `SUCCESS`
- 续租操作锁
- 写确认成功日志
- 尝试触发 `tryFinalizeDraft(...)`

这一步是“外部成功结果回流到工作流状态机”的关键桥梁。

### 3. `tryFinalizeDraft`

这是发布真正收口的地方。

它会检查：

- 草稿当前版本是不是这个版本
- 草稿当前状态是不是 `PUBLISHING`
- 当前版本是否存在 `DEAD` 任务
- 当前版本的所有任务是否都已 `SUCCESS`

只有全部满足，草稿才会从 `PUBLISHING` 进入 `PUBLISHED`。

所以要记住一句话：

**当前项目里，发布最终完成不是 `publish(...)` 决定的，而是 `PublishTaskProgressService.tryFinalizeDraft(...)` 决定的。**

## 十六、`WorkflowSideEffectConsumerService`：下游成功如何反馈回来

如果没有这层，任务就会一直卡在 `AWAITING_CONFIRMATION`。

这个类的职责是：

- 接收搜索刷新成功消息
- 接收读模型同步成功消息
- 接收发布通知成功消息
- 调用 `PublishTaskProgressService.confirmTaskSuccess(...)`

也就是说，它把“下游成功”翻译成“工作流任务成功”。

这是当前项目从“本地伪异步”走向“有明确确认语义的异步流程”的关键一环。

## 十七、失败链路怎么处理

读异步任务时，不要只看成功路径，失败路径更能体现工程质量。

### 1. 任务自动失败重试

在 `PublishTaskWorker.markFailedOrDead(...)` 里可以看到：

- 失败后会累计 `retryTimes`
- 根据指数退避设置 `nextRunAt`
- 超过上限进入 `DEAD`

这解决的是短期抖动问题。

### 2. 不可恢复失败如何影响草稿

如果任务进入 `DEAD`，会继续调用：

- `markDraftFailedAndCompensate(...)`

这一步会：

- 把草稿从 `PUBLISHING` 改成 `PUBLISH_FAILED`
- 写失败日志
- 发布失败事件
- 对已成功任务尝试做 best-effort 补偿

这说明项目承认一种真实状态：

- 主事务已经成功
- 但最终发布没有完成

所以需要一个明确的 `PUBLISH_FAILED` 状态。

## 十八、`WorkflowRecoveryService`：失败之后不是手改数据库

这部分非常值得新人认真看，因为很多 demo 系统只做 happy path，这个项目把恢复也做成了正式流程。

### 1. 它提供哪些能力

主要包括：

- 查询可恢复发布任务
- 手动重试单个失败任务
- 批量重试当前版本失败任务
- 查询可恢复 outbox 事件
- 手动重试 outbox 事件

### 2. 它有哪两个关键限制

#### 第一，只允许恢复当前 `publishedVersion`

这是为了避免误操作旧版本任务，把历史脏状态重新拉活。

#### 第二，恢复也要受草稿级操作锁控制

源码里可以看到它也会：

- 读取当前锁
- 校验是否过期
- 尝试获取或复用锁

这说明恢复不是“旁路操作”，而是正式参与工作流互斥控制的。

### 3. 恢复后草稿状态会怎么变

如果草稿当前是 `PUBLISH_FAILED`，恢复任务时会尝试把它重新推进到 `PUBLISHING`。

这一步非常重要，因为只有回到 `PUBLISHING`，后续任务成功后系统才可能再次收敛到 `PUBLISHED`。

## 十九、Outbox 链路源码怎么看

如果你已经理解发布任务链路，再看 Outbox 会轻松很多。

关键类是：

- `WorkflowEventPublisher`
- `OutboxWorkflowEventPublisher`
- `OutboxRelayWorker`
- `OutboxEventEntity`

### 1. 业务代码怎么发事件

业务层通常不直接调用 RabbitMQ。

它只调用：

- `WorkflowEventPublisher.publish(...)`

如果启用了 Outbox，实际注入的是：

- `OutboxWorkflowEventPublisher`

这个实现会先把事件写进 outbox 表。

### 2. `OutboxRelayWorker` 做什么

它负责：

- 批量 claim 可发送事件
- 标记为 `SENDING`
- 真正调用 `rabbitTemplate.send(...)`
- 成功则标记 `SENT`
- 失败则进入 `FAILED` 或 `DEAD`

这条链路和发布任务链路很像，都是：

- 主流程只可靠落库
- 后台 worker 负责异步推进

## 二十、看源码时要重点抓住的几个“不变量”

如果你觉得类多、方法多，建议抓住下面这些不变量去读。

### 1. 草稿状态和任务状态不是一回事

- 草稿状态看整体阶段
- 任务状态看局部副作用进度

### 2. 快照是不可变历史版本

- 草稿会继续编辑
- 快照不会被回写覆盖

### 3. 发布接口只负责把事情“落稳”，不负责把事情“全部做完”

这就是为什么会有 `PUBLISHING`。

### 4. 真正的发布完成依赖任务全部成功并被确认

不是依赖接口返回。

### 5. 同一草稿的关键操作需要串行化

这就是草稿级操作锁存在的原因。

### 6. 失败恢复是正式流程，不是临时补丁

所以它有独立接口、独立日志和独立校验。

## 二十一、如果你要改代码，通常该从哪一层下手

最后给一个非常实用的定位建议。

### 1. 要加新接口或改请求参数

优先看：

- `workflow.interfaces`
- `dto`
- `vo`

### 2. 要改状态流转或业务规则

优先看：

- `InMemoryContentWorkflowService`
- `PublishTaskProgressService`
- `WorkflowRecoveryService`

### 3. 要加新的副作用任务类型

优先看：

- `PublishTaskType`
- `DefaultPublishTaskHandlers`
- `PublishTaskEventFactory`
- `WorkflowSideEffectConsumerService`

### 4. 要改数据库持久化和并发控制

优先看：

- `WorkflowStore`
- `JpaWorkflowStore`
- `workflow.infrastructure.persistence.*`

### 5. 要改消息可靠投递

优先看：

- `common.messaging.outbox.*`
- `OutboxRelayWorker`

## 二十二、给实习生的一句总结

第一次看这个项目时，可以只抓住一句话：

**这是一个把“内容发布”拆成同步主事务、异步副作用、消息可靠投递和失败恢复四条链路的工作流系统。**

只要你先把这四条链路和各自负责人对应上，后面再看具体类和方法，就不会再觉得代码是散的。

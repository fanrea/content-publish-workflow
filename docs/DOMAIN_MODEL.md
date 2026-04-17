# 领域模型说明

这份文档回答的是一个很基础、但非常重要的问题：

“这个项目里到底有哪些核心业务对象，它们分别代表什么？”

第一次接触项目时，很多人会把这些对象都理解成“数据库里的几张表”。这种理解不完全错，但不够用。因为在这个项目里，每个对象都有明确业务语义，它们不是简单的数据容器。

如果你把对象语义理解错了，后面看发布、回滚、恢复时就很容易混乱。

## 一、先建立一个整体概念

当前项目主要围绕六类核心对象运转：

1. 当前工作副本：`ContentDraft`
2. 审核历史：`ReviewRecord`
3. 历史发布版本：`ContentSnapshot`
4. 发布副作用任务：`PublishTask`
5. 发布命令记录：`PublishCommandEntry`
6. 结构化审计日志：`PublishLogEntry`

除此之外，还有两类“支撑运行但非常关键”的对象：

7. 草稿级操作锁：`DraftOperationLockEntry`
8. Outbox 事件：`OutboxEventEntity`

你可以把它们想象成一套协作关系：

```text
ContentDraft
  -> 产生 ReviewRecord
  -> 产生 ContentSnapshot
  -> 产生 PublishTask
  -> 产生 PublishCommandEntry
  -> 产生 PublishLogEntry
  -> 关联 DraftOperationLockEntry

WorkflowEvent
  -> OutboxEventEntity
```

## 二、聚合根：`ContentDraft`

`ContentDraft` 是整个工作流里最核心的对象。

你可以把它理解成：

“当前这篇内容正在被编辑、审核、发布、回滚或下线时，系统里那份始终会被更新的主对象。”

它代表的是**当前工作副本**，不是历史版本。

### 1. 它负责表达什么

- 当前正在编辑的标题、摘要、正文
- 当前工作流状态
- 当前草稿版本号
- 当前已发布版本号
- 最近一次审核驳回意见
- 当前线上版本对应的快照引用

### 2. 为什么它是聚合根

因为很多动作最终都要回到它身上体现：

- 编辑草稿时，它更新内容和草稿版本
- 提交审核时，它从 `DRAFT` 进入 `REVIEWING`
- 审核通过时，它变成 `APPROVED`
- 发起发布时，它变成 `PUBLISHING`
- 发布全部完成后，它变成 `PUBLISHED`
- 发布失败时，它变成 `PUBLISH_FAILED`
- 下线时，它变成 `OFFLINE`

也就是说，它是系统里的“当前真相”。

### 3. 重点字段怎么理解

- `id`
  内部主键。

- `bizNo`
  业务编号。它和版本无关，适合做跨版本稳定识别。

- `title`、`summary`、`body`
  当前工作副本内容。

- `draftVersion`
  草稿版本号。每次编辑都会递增。

- `publishedVersion`
  发布版本号。每次发布成功发起，或者基于历史快照发起回滚版本时都会递增。

- `status`
  当前工作流状态。

- `currentSnapshotId`
  当前线上版本对应的快照 ID。

- `lastReviewComment`
  最近一次审核驳回意见。

### 4. 新人最容易误解的地方

不要把 `ContentDraft` 理解成“线上内容本身”。

它更像编辑台上的“当前工作稿”。线上真正稳定可追溯的版本，是 `ContentSnapshot`。

## 三、审核历史：`ReviewRecord`

`ReviewRecord` 表示一次审核动作的留痕。

它回答的是：

- 谁审核了
- 审的是哪一版草稿
- 审核结果是什么
- 留了什么意见
- 在什么时候审核的

### 1. 为什么它不能只放在草稿里

如果只在 `ContentDraft` 里放一个“当前审核结果”，会丢掉很多重要历史：

- 某个版本以前被谁驳回过
- 审核通过前改了几次
- 审核意见是什么

所以 `ReviewRecord` 是追加型历史对象，不应该和草稿当前状态混在一起。

### 2. 重点字段

- `draftId`
- `draftVersion`
- `reviewer`
- `decision`
- `comment`
- `reviewedAt`

这里尤其要注意 `draftVersion`。它让系统知道“审核的不是抽象草稿，而是草稿在某个具体编辑版本上的状态”。

## 四、发布快照：`ContentSnapshot`

`ContentSnapshot` 表示“某一次已经确定对外生效的发布内容副本”。

它是不可变历史版本。

### 1. 为什么项目里必须有快照

因为草稿会继续被编辑。

如果没有快照，会出现这些问题：

- 你不知道线上版本到底对应哪次发布
- 回滚时找不到稳定基线
- 发布 diff 没有可靠对比对象
- 审计很难回答“当时到底发布了什么内容”

### 2. 它和草稿最大的区别

- `ContentDraft`：当前还会继续变
- `ContentSnapshot`：一旦生成，不应该再变

### 3. 重点字段

- `draftId`
- `publishedVersion`
- `sourceDraftVersion`
- `title`
- `summary`
- `body`
- `operator`
- `rollback`
- `publishedAt`

### 4. `rollback` 字段是什么意思

它不是说“这份快照是回滚回来的旧数据”，而是说：

“这个发布版本是通过回滚操作生成出来的新发布版本。”

这能帮助系统区分：

- 普通发布
- 基于历史版本触发的回滚发布

## 五、发布任务：`PublishTask`

`PublishTask` 是理解这个项目的关键对象之一。

它代表的不是“发布动作本身”，而是“发布过程中需要执行的某个副作用任务”。

比如一次发布可能要做：

- 刷新搜索索引
- 同步下游读模型
- 发送发布通知

这些事情不适合直接塞进主事务里，所以被拆成独立任务。

### 1. 为什么任务要成为独立对象

因为在真实系统里经常会出现：

- 草稿主事务已经成功
- 但搜索刷新失败了
- 通知还没发出去
- 读模型同步还在等待下游确认

如果没有 `PublishTask`，这些中间状态就表达不清楚，也很难做重试和恢复。

### 2. 当前支持的任务类型

- `REFRESH_SEARCH_INDEX`
- `SYNC_DOWNSTREAM_READ_MODEL`
- `SEND_PUBLISH_NOTIFICATION`

### 3. 当前任务状态

当前代码中的任务状态是：

- `PENDING`
  已创建，等待 worker 领取。

- `RUNNING`
  worker 已领取，正在处理。

- `AWAITING_CONFIRMATION`
  请求已经发给下游，但系统还在等下游明确确认成功。

- `SUCCESS`
  下游已确认成功。

- `FAILED`
  失败，但还能自动重试。

- `DEAD`
  自动重试耗尽，需要人工介入。

### 4. 重点字段

- `draftId`
- `publishedVersion`
- `taskType`
- `status`
- `retryTimes`
- `errorMessage`
- `nextRunAt`
- `lockedBy`
- `lockedAt`

### 5. 为什么 `AWAITING_CONFIRMATION` 很重要

这是当前项目和很多简单 demo 最大的不同之一。

它表达了一个现实：

“我把副作用请求发出去了”不等于“下游真的完成了”。

所以任务需要一个中间状态，表示：

- 本地 worker 的工作已经完成
- 但最终成功还要等下游确认

## 六、发布命令：`PublishCommandEntry`

这个对象是用来解决幂等问题的。

它表达的是：

“某次发布请求本身，在系统里被当成一个可查询、可追踪的命令对象保存了下来。”

### 1. 它解决什么问题

如果用户因为网络抖动、重复点击、前端重试等原因多次调用发布接口，系统需要知道：

- 这是不是同一个发布请求
- 是不是已经处理过
- 能不能复用上一次结果
- 会不会重复生成快照和任务

### 2. 重点字段

- `draftId`
- `commandType`
- `idempotencyKey`
- `status`
- `targetPublishedVersion`
- `snapshotId`
- `errorMessage`

### 3. 它不是业务结果本身

这个对象不是为了表示“发布成功了什么内容”，而是为了表示：

“一次发布请求在系统中被如何接收和处理。”

它更像命令台账。

## 七、结构化审计日志：`PublishLogEntry`

`PublishLogEntry` 不是普通文本日志，而是结构化业务日志。

它的用途包括：

- 给页面展示时间线
- 排查一次发布发生了什么
- 关联请求、操作人、任务、outbox 事件
- 记录恢复动作和失败原因

### 1. 它记录哪些维度

- 谁操作的
- 操作了什么
- 操作对象是谁
- 之前什么状态
- 之后什么状态
- 是否成功
- 错误码和错误信息是什么
- 当前链路的 `traceId`、`requestId` 是什么

### 2. 为什么这个对象很重要

如果没有它，很多排障问题会变成“去翻程序打印日志”。

有了它以后，很多问题可以直接从业务层视角回答：

- 某次发布为什么失败
- 哪个任务失败了
- 是自动重试失败还是人工恢复过
- 某个版本何时完成发布

## 八、草稿级操作锁：`DraftOperationLockEntry`

这是当前项目里一个很重要的新对象。

它不是业务主数据，但它对保障流程正确性非常关键。

### 1. 它解决什么问题

它用于串行化同一篇草稿上的关键互斥操作，比如：

- 发布
- 回滚
- 下线
- 对当前版本失败任务做恢复

如果没有这把锁，可能出现：

- 两次发布同时开始
- 发布中又发起回滚
- 恢复任务时另一个线程又改了状态

### 2. 它的特点

- 是草稿级别的锁，不是任务级别的锁
- 是租约锁，不是永久锁
- 有 `expiresAt`，过期后可以被接管
- 成功链路通常会释放
- 异常情况下即使没释放，也能靠过期时间兜底

### 3. 重点字段

- `draftId`
- `operationType`
- `targetPublishedVersion`
- `lockedBy`
- `lockedAt`
- `expiresAt`

### 4. `operationType` 的含义

当前支持：

- `PUBLISH`
- `ROLLBACK`
- `OFFLINE`

它表示“当前草稿正在被哪种关键操作占用”。

## 九、Outbox 事件：`OutboxEventEntity`

`OutboxEventEntity` 是系统和 MQ 之间的缓冲层对象。

它的存在是为了确保：

- 主业务事务提交成功后，消息一定有机会被继续投递
- 不需要在数据库事务里直接发 MQ

### 1. 它记录什么

- 事件类型
- 聚合类型和聚合 ID
- 聚合版本
- 交换机和路由键
- payload 和 headers
- 当前投递状态
- 重试次数
- 锁定信息
- 错误信息

### 2. 为什么它不直接等同于消息

因为 MQ 里的消息是瞬时传输对象，而 `OutboxEventEntity` 是数据库里的可靠待办记录。

它的价值是：即使 RabbitMQ 短时不可用，业务事务也不用跟着一起失败。

## 十、几种“状态”一定要分开理解

第一次看项目时，最容易混淆的是不同层级的状态。

### 1. 草稿状态 `WorkflowStatus`

它描述的是“这篇内容整体走到哪个阶段了”。

比如：

- `DRAFT`
- `REVIEWING`
- `APPROVED`
- `PUBLISHING`
- `PUBLISHED`
- `PUBLISH_FAILED`
- `OFFLINE`

### 2. 任务状态 `PublishTaskStatus`

它描述的是“某个副作用任务进度如何”。

比如：

- `PENDING`
- `RUNNING`
- `AWAITING_CONFIRMATION`
- `SUCCESS`
- `FAILED`
- `DEAD`

### 3. Outbox 状态 `OutboxEventStatus`

它描述的是“消息投递动作进行到哪了”。

比如：

- `NEW`
- `SENDING`
- `SENT`
- `FAILED`
- `DEAD`

这三类状态分别解决的是不同问题，不能混用。

## 十一、两个版本号也不能混为一谈

项目里至少有两个非常重要的版本概念。

### 1. `draftVersion`

它表示编辑工作副本的版本推进。

适合回答这些问题：

- 审核审的是哪一版草稿
- 某次编辑之后内容变了几次
- 这次发布来源于哪个草稿版本

### 2. `publishedVersion`

它表示对外发布版本。

适合回答这些问题：

- 当前线上是什么版本
- 回滚要回到哪个历史版本
- 某次快照对应哪个发布版本
- 一条发布时间线针对的是哪个版本

如果把这两个版本合并，很多业务语义会变得模糊。

## 十二、给第一次看项目的人一个简单记忆法

可以把这些对象按“现在、历史、过程、保护”四类来记：

- 现在：`ContentDraft`
- 历史：`ReviewRecord`、`ContentSnapshot`
- 过程：`PublishTask`、`PublishCommandEntry`、`PublishLogEntry`、`OutboxEventEntity`
- 保护：`DraftOperationLockEntry`

记住这层划分后，再看源码时就更容易判断：

- 这个对象是在表达当前真相
- 还是在表达历史留痕
- 还是在表达异步过程
- 还是在保护并发正确性

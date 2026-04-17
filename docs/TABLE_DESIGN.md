# 表设计说明

这份文档的目标不是把建表 SQL 一行行翻译出来，而是帮助第一次看项目的人理解：

1. 为什么这个系统需要这么多表。
2. 每张表在整个工作流里扮演什么角色。
3. 表和表之间是怎么配合完成“发布、回滚、恢复、审计、消息投递”这些能力的。

如果你只是从“内容管理系统”视角看，很容易觉得这些表有点多。但从“真实发布工作流系统”视角看，这些拆分是有必要的。

## 一、先记住整体设计原则

当前表结构主要遵循四条原则：

### 1. 当前工作副本和历史发布版本分开

- `content_draft` 保存当前可编辑工作副本
- `content_publish_snapshot` 保存历史发布版本快照

### 2. 主业务数据和副作用执行过程分开

- `content_publish_task` 保存副作用任务
- `workflow_outbox_event` 保存待投递消息事件

### 3. 请求命令、业务结果、审计日志分开

- `content_publish_command` 记录幂等命令
- `content_publish_log` 记录结构化审计时间线

### 4. 关键互斥操作需要显式加锁

- `draft_operation_lock` 负责草稿级租约锁

把这四条原则记住后，再看每张表就不容易迷路。

## 二、总表关系可以先这么看

```text
content_draft
  -> content_review_record
  -> content_publish_snapshot
  -> content_publish_task
  -> content_publish_command
  -> content_publish_log
  -> draft_operation_lock

业务事件
  -> workflow_outbox_event
```

这里的核心表是 `content_draft`，其他很多表都围绕它展开，但含义各不相同：

- 有的是历史表
- 有的是过程表
- 有的是控制表

## 三、`content_draft`：当前工作副本主表

这是系统的主业务表。

### 1. 它保存什么

- 当前标题、摘要、正文
- 当前草稿版本
- 当前发布版本
- 当前工作流状态
- 最近一次审核驳回意见
- 当前线上快照引用

### 2. 它回答什么问题

- 这篇内容现在处于哪个阶段
- 当前编辑内容是什么
- 上一次发布到哪个版本了
- 当前是否在审核中 / 发布中 / 已下线

### 3. 重点字段

- `id`
- `lock_version`
- `biz_no`
- `title`
- `summary`
- `body`
- `draft_version`
- `published_version`
- `workflow_status`
- `current_snapshot_id`
- `last_review_comment`
- `created_at`
- `updated_at`

### 4. 为什么需要 `lock_version`

这是乐观锁字段。

它主要用来防止：

- 旧请求把新状态覆盖掉
- 并发更新把状态机写乱

当前代码里的条件更新会同时结合：

- `lock_version`
- 允许的旧状态集合

来判断本次更新是否合法。

### 5. 重要索引为什么这么建

- `uk_content_draft_biz_no`
  保证业务编号唯一。

- `idx_content_draft_status`
  方便按状态筛选草稿。

- `idx_content_draft_updated_at`
  方便按更新时间排序列表。

- `idx_content_draft_created_at`
  方便按创建时间排序列表。

- `idx_content_draft_status_updated`
- `idx_content_draft_status_created`
  方便“按状态过滤 + 按时间排序”的典型列表页场景。

## 四、`draft_operation_lock`：草稿级操作锁表

这是当前项目里很重要的一张控制表。

### 1. 它解决什么问题

用于串行化同一篇草稿上的互斥动作，比如：

- 正在发布时，不允许同时回滚
- 正在回滚时，不允许同时下线
- 正在恢复当前版本任务时，不希望别的线程又发起同一草稿的关键操作

### 2. 为什么要单独建表

因为这把锁不是草稿业务内容本身，而是流程控制状态。

如果把锁信息直接塞进 `content_draft`，会让：

- 草稿主数据语义变脏
- 锁过期和锁接管逻辑变难写
- 并发控制和主业务字段耦合

### 3. 重点字段

- `draft_id`
  也是主键，表示同一篇草稿同一时刻只保留一把有效锁记录。

- `operation_type`
  表示锁住的是哪种关键操作。

- `target_published_version`
  表示这把锁保护的是哪个目标发布版本，便于区分不同版本动作。

- `locked_by`
- `locked_at`
- `expires_at`

### 4. 重要索引

- `idx_draft_operation_lock_expires`
  方便扫描过期锁和接管锁。

### 5. 这张表的一个关键特点

它用的是租约锁思路，而不是“永不失效的数据库锁”。

也就是说：

- 正常情况下流程会主动释放
- 异常情况下也能靠过期时间兜底

## 五、`content_review_record`：审核历史表

这张表专门保存审核留痕。

### 1. 它记录什么

- 哪篇草稿
- 哪一版草稿
- 谁审核的
- 审核结果是什么
- 审核意见是什么
- 审核时间是什么时候

### 2. 为什么不能只保存在草稿当前字段里

因为审核记录本质上是历史轨迹，不是当前状态。

如果只在主表上保留当前审核意见，会失去：

- 多次审核过程
- 审核针对的具体草稿版本
- 完整审核链路

### 3. 重点索引

- `idx_review_draft_version`
  方便按草稿和草稿版本回看审核记录。

- `idx_review_draft_time`
  方便按时间查看某篇草稿的审核历史。

## 六、`content_publish_snapshot`：历史发布快照表

这是系统里保存“历史线上版本”的表。

### 1. 它记录什么

- 某篇草稿的某个发布版本
- 该版本发布时的标题、摘要、正文
- 该版本来源于哪个草稿版本
- 操作人是谁
- 是否由回滚生成
- 发布时间

### 2. 为什么它非常关键

它支撑了几个核心能力：

- 历史版本追溯
- 发布 diff 对比
- 回滚目标查找
- 版本时间线展示

### 3. 关键约束

- `uk_snapshot_draft_version (draft_id, published_version)`

这个唯一约束表示：

同一篇草稿的同一个发布版本，只能有一份快照。

### 4. 重点索引

- `idx_snapshot_draft_time`
  方便按时间看某篇草稿的发布历史。

## 七、`content_publish_task`：发布副作用任务表

这是项目里最有“工作流味道”的一张表。

### 1. 它记录什么

一次发布或回滚会生成若干副作用任务，这张表保存这些任务的运行状态。

### 2. 为什么必须单独成表

因为发布不是一个瞬间动作，而是由多个可失败、可重试、可恢复的副作用组成。

如果不拆表，就很难表达下面这些真实状态：

- 搜索刷新成功了，但通知还没发
- 下游请求已经发出，但还没确认
- 某个任务失败了，过 30 秒再重试
- 自动重试耗尽，需要人工介入

### 3. 重点字段

- `draft_id`
- `published_version`
- `task_type`
- `task_status`
- `retry_times`
- `error_message`
- `next_run_at`
- `locked_by`
- `locked_at`
- `created_at`
- `updated_at`

### 4. 当前状态字段怎么理解

- `PENDING`
  等待 worker 领取。

- `RUNNING`
  worker 正在执行。

- `AWAITING_CONFIRMATION`
  本地已发出请求，等待下游明确确认成功。

- `SUCCESS`
  下游已确认成功。

- `FAILED`
  自动重试前的失败中间态。

- `DEAD`
  自动重试耗尽，需人工介入。

### 5. 唯一约束为什么重要

- `uk_task_draft_version_type (draft_id, published_version, task_type)`

它保证：

同一篇草稿、同一个发布版本、同一种任务类型，只会创建一条任务记录。

这对避免重复副作用非常关键。

### 6. 重要索引

- `idx_task_status`
  方便按状态查任务。

- `idx_task_status_next_run`
  方便 worker 扫描“现在可以执行”的任务。

- `idx_task_lock`
  方便处理锁超时和并发领取。

- `idx_task_draft_status`
  方便按草稿查看不同状态任务。

## 八、`content_publish_command`：发布命令与幂等表

这张表解决的是“请求级别的幂等”。

### 1. 它记录什么

- 哪篇草稿
- 哪种命令类型
- 幂等键是什么
- 操作人是谁
- 目标发布版本是什么
- 命令当前状态是什么
- 是否关联了快照
- 失败原因是什么

### 2. 它的核心价值

它让系统能分辨：

- 这次调用是新请求
- 还是某次旧请求的重复提交

### 3. 关键唯一约束

- `uk_publish_command (draft_id, command_type, idempotency_key)`

这保证同一草稿的同一命令类型下，相同幂等键只能出现一次。

### 4. 为什么单独存表而不是放缓存

因为幂等语义需要可靠持久化。

如果只放缓存，会遇到：

- 服务重启后丢失
- 不能做长期审计
- 不能在恢复和排障时查历史命令状态

## 九、`content_publish_log`：结构化审计日志表

这张表不是“程序运行日志”，而是“业务动作时间线”。

### 1. 它记录什么

- 草稿 ID
- `trace_id`
- `request_id`
- `action_type`
- 操作人 ID / 名称
- 目标对象类型和目标对象 ID
- 发布版本
- 任务 ID
- outbox 事件 ID
- 前后状态
- 结果
- 错误码
- 错误信息
- 备注
- 时间

### 2. 为什么单独成表

因为系统需要从业务视角回答这些问题：

- 这次发布从什么时候开始的
- 哪一步失败了
- 是谁触发的恢复
- 这个任务是自动失败还是人工重试的

程序控制台日志很难稳定回答这些问题，而结构化表记录可以。

### 3. 重点索引

- `idx_log_draft_time`
  方便按草稿查看时间线。

- `idx_log_trace_id`
  方便按链路 ID 聚合一次完整请求或发布流程。

## 十、`workflow_outbox_event`：可靠消息中间表

这是 Outbox 模式的核心表。

### 1. 它记录什么

- 事件 ID
- 事件类型
- 聚合类型
- 聚合 ID
- 聚合版本
- 交换机
- 路由键
- payload JSON
- headers JSON
- 当前状态
- 投递尝试次数
- 下次重试时间
- 锁定信息
- 错误信息
- 发送时间

### 2. 为什么需要它

它解决的是数据库事务和 MQ 发送之间的可靠衔接问题。

主业务事务里只需要：

- 写主表
- 写 outbox 表

然后由 `OutboxRelayWorker` 异步发送到 MQ。

这样能避免：

- 数据提交了，但消息没发出去
- 消息发出去了，但业务事务回滚了

### 3. 重要索引

- `idx_outbox_status_next`
  方便扫描“现在可以发送/重试”的事件。

- `idx_outbox_locked`
  方便处理锁过期或 worker 崩溃后的接管。

- `idx_outbox_aggregate`
  方便按业务对象追踪相关事件。

## 十一、为什么不把这些信息都塞进一两张表

这是新人最常问的问题之一。

短答案是：因为业务语义不同，生命周期不同，查询模式也不同。

### 1. 如果把历史快照塞进主表

会导致：

- 当前工作副本和历史版本混在一起
- 回滚和 diff 非常难做
- 历史不可追溯

### 2. 如果把任务状态塞进主表

会导致：

- 一个草稿同时只能表达一个副作用状态
- 不能细粒度重试某个任务
- 无法记录不同任务类型的独立进度

### 3. 如果把命令和日志都塞进主表

会导致：

- 主表越来越臃肿
- 请求级幂等和业务状态耦合
- 排障查询非常难写

### 4. 如果不用专门的锁表

会导致：

- 关键操作并发控制不清晰
- 锁语义和业务字段纠缠
- 多实例扩展时风险增大

所以这些拆表不是为了“显得架构复杂”，而是为了让每张表只承担一类稳定职责。

## 十二、给第一次看表结构的人一个阅读建议

可以按下面顺序看数据库：

1. 先看 `content_draft`，理解当前工作副本是什么。
2. 再看 `content_publish_snapshot`，理解历史版本为什么分离。
3. 再看 `content_publish_task`，理解发布为什么不是瞬间完成。
4. 再看 `content_publish_command` 和 `content_publish_log`，理解幂等和审计如何落地。
5. 再看 `draft_operation_lock`，理解互斥操作怎么被串行化。
6. 最后看 `workflow_outbox_event`，理解消息可靠投递是怎么做的。

按这个顺序看，表不会再只是孤立的 SQL，而会变成一条完整工作流在数据库里的投影。

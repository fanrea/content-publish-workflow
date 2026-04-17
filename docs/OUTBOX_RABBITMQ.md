# Outbox 与 RabbitMQ 说明

这份文档解释项目中的异步消息链路为什么存在、如何工作，以及它和发布主链路之间的关系。

## 一、为什么要有 Outbox

如果在发布主事务里直接发 MQ，会有一个经典问题：

- 数据库事务提交成功，但 MQ 发送失败
- MQ 发送成功，但数据库事务回滚

这两种情况都会让系统进入难以恢复的不一致状态。

Outbox 模式的核心思路是：

1. 主事务里只写数据库和 outbox 表
2. 不在主事务里直接发 MQ
3. 由独立 relay worker 再去投递 MQ

这样主事务只需要保证一件事：

“业务数据和待发送事件同时可靠落库。”

## 二、当前实现结构

项目的消息链路主要由以下部分组成：

- `WorkflowEvent`
- `WorkflowEventPublisher`
- `OutboxWorkflowEventPublisher`
- `OutboxEventEntity`
- `OutboxEventRepository`
- `OutboxRelayWorker`
- `WorkflowSideEffectConsumerService`
- `WorkflowMessageDeduplicationGuard`

## 三、事件是何时产生的

当前系统在以下场景会产出工作流事件：

- 发布成功
- 发布失败
- 搜索刷新请求
- 读模型同步请求
- 发布通知请求

这些事件最终会被持久化到 `workflow_outbox_event` 表。

## 四、Outbox 表的职责

`workflow_outbox_event` 不是普通日志表，而是一个“待投递消息队列表”。

它至少需要保存：

- `event_id`
- `event_type`
- `aggregate_type`
- `aggregate_id`
- `aggregate_version`
- `exchange_name`
- `routing_key`
- `payload_json`
- `headers_json`
- `status`
- `attempt`
- `next_retry_at`
- `locked_by`
- `locked_at`
- `error_message`

状态包括：

- `NEW`
- `SENDING`
- `SENT`
- `FAILED`
- `DEAD`

## 五、投递流程

完整流程如下：

1. 业务代码创建 `WorkflowEvent`
2. `OutboxWorkflowEventPublisher` 把事件序列化为 `OutboxEventEntity`
3. 主事务提交时，事件和业务数据一起落库
4. `OutboxRelayWorker` 周期性领取可发送事件
5. 事件状态切到 `SENDING`
6. worker 调用 `RabbitTemplate` 发送消息
7. 发送成功则标记为 `SENT`
8. 发送失败则进入 `FAILED`，并带上退避重试时间
9. 超过最大重试次数则进入 `DEAD`

## 六、为什么还需要锁和重试

因为系统可能有多个 worker 实例同时运行，如果不做领取和锁定控制，就会发生重复发送。

所以 outbox 表设计了：

- `locked_by`
- `locked_at`
- `next_retry_at`

它们分别用来解决：

- 谁领取了这条消息
- 何时领取的
- 失败后什么时候可以再次重试

## 七、RabbitMQ 在项目中的位置

RabbitMQ 在当前项目里扮演的是“异步副作用总线”的角色，而不是主业务事实来源。

也就是说：

- 事实来源仍然是数据库
- RabbitMQ 负责把异步动作通知给外部消费者

当前拓扑支持的典型队列有：

- 搜索索引刷新队列
- 读模型同步队列
- 发布通知队列

## 八、消费端做了什么

消费端目前的工程能力已经接好，重点包括：

- 监听入口
- 消费防重
- 消费审计日志
- 调用副作用网关

目前下游网关还是 stub 或接缝实现，但整个工程链路已经存在。

## 九、消费防重

消息系统里常见问题不是“收不到”，而是“可能重复收到”。

项目使用 `WorkflowMessageDeduplicationGuard` 结合缓存做了一层轻量去重：

- 如果 `messageId` 没处理过，则允许消费
- 如果已处理过，则跳过重复消费

这层防重不是严格分布式事务，但足够支撑当前演示和常见重放问题。

## 十、如何启用

启用 RabbitMQ 相关能力需要分几层看：

### 1. 打开 profile

```bash
SPRING_PROFILES_ACTIVE=rabbitmq
```

### 2. 打开 outbox 持久化

```yaml
workflow:
  outbox:
    enabled: true
```

### 3. 打开 relay

```yaml
workflow:
  outbox:
    relay:
      enabled: true
```

### 4. 如需消费端，再单独打开 consumer

当前默认 consumer 也是关闭的。

## 十一、失败与恢复

### 1. 失败状态

发送失败的 outbox 事件会进入：

- `FAILED`
- 或 `DEAD`

### 2. 人工恢复

项目提供了恢复接口：

- `GET /outbox/events/recovery`
- `POST /outbox/events/{outboxEventId}/manual-retry`

恢复时会把事件重新置为：

- `status=NEW`
- `attempt=0`
- 清空锁和错误信息

## 十二、这个设计的价值

这套设计的价值在于：

- 主事务与消息发送解耦
- 可重试
- 可追踪
- 可人工恢复
- 容易讲清楚最终一致性

对于一个工作流系统来说，这比“发布时直接调三个外部接口”要稳健得多。

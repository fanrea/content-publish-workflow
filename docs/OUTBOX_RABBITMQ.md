# Outbox And RabbitMQ Guide

这份文档面向本地联调，重点解释三个问题：

1. 为什么项目里有 outbox。
2. RabbitMQ 相关功能默认为什么看起来“没动静”。
3. 真正要联调消息链路时，应该开哪些开关。

## 先说项目里的真实语义

数据库是业务事实源，RabbitMQ 不是。

当前项目里，发布后的异步副作用不会在主事务里直接发 MQ，而是走 outbox：

1. 主事务把业务数据和 outbox 事件一起落库
2. relay worker 轮询 `workflow_outbox_event`
3. relay 再把事件投递到 RabbitMQ

这样做的目的是避免：

- 数据库提交成功，但 MQ 发送失败
- MQ 发送成功，但数据库事务回滚

## 默认为什么看不到 MQ 消息

因为 `rabbitmq` profile 只接上 MQ 连接参数，不会默认把 relay 打开。

当前默认语义是：

- `workflow.outbox.enabled=true`
- `workflow.outbox.relay.enabled=false`
- `workflow.outbox.topology.consumer.enabled=false`

所以常见现象是：

- 发布动作后 outbox 表里有事件
- 但 RabbitMQ 里没有消息

这通常不是 bug，而是 relay 没开。

## 本地联调最小步骤

### 1. 启动 RabbitMQ

```powershell
docker compose -f compose.local.yml up -d rabbitmq
```

默认账号：

- User: `cpw`
- Password: `cpw`
- UI: [http://127.0.0.1:15672](http://127.0.0.1:15672)

### 2. 打开 RabbitMQ profile

```powershell
$env:SPRING_PROFILES_ACTIVE="rabbitmq"
```

### 3. 真正开启 relay

```powershell
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
```

### 4. 启动应用

```powershell
mvn spring-boot:run
```

如果你还想同时本地登录和看 actuator，常见组合是：

```powershell
$env:SPRING_PROFILES_ACTIVE="redis,rabbitmq,demo,ops"
$env:WORKFLOW_OUTBOX_RELAY_ENABLED="true"
mvn spring-boot:run
```

## 消费端为什么默认也没开

因为这个项目把“生产事件”和“消费副作用”拆开了，避免你本地联调时一次性把变量拉满。

默认：

- 生产端可以落 outbox
- relay 可以按需开启
- consumer 仍然是关闭的

如果你要把消费端也一起打通，再开：

```powershell
$env:WORKFLOW_OUTBOX_TOPOLOGY_CONSUMER_ENABLED="true"
```

## outbox 表里你应该看到什么

核心字段通常包括：

- `event_id`
- `event_type`
- `aggregate_type`
- `aggregate_id`
- `payload_json`
- `headers_json`
- `trace_id`
- `request_id`
- `status`
- `attempt`
- `next_retry_at`
- `locked_by`
- `locked_at`
- `error_message`

状态流转大致是：

- `NEW`
- `SENDING`
- `SENT`
- `FAILED`
- `DEAD`

## 本地排障顺序

### 1. outbox 有记录，但 RabbitMQ 没消息

先查：

- 是否开了 `rabbitmq` profile
- 是否设置了 `WORKFLOW_OUTBOX_RELAY_ENABLED=true`
- worker 是否在跑
- 事件状态是不是一直停在 `NEW`

### 2. 事件一直在 `FAILED`

常见原因：

- RabbitMQ 连不上
- 交换机或路由键配置错误
- broker 在线，但声明拓扑失败

优先看：

- 应用日志
- RabbitMQ 管理台
- outbox 表中的 `error_message`

### 3. 事件进了 `DEAD`

这说明重试次数已经打满。

当前项目支持恢复和人工重试，排障时先确认：

- 根因是否已经修复
- 重试后是否还会继续失败

### 4. 怀疑重复发送

先分清：

- 是同一条 outbox 事件被重复发送
- 还是同一业务动作本来就产生了多条事件

如果是 worker 层面重复触发，先排查调度模式是否混成了本地 `@Scheduled` 和 XXL-Job 同时开启。

## 和调度的关系

outbox relay 本身也是 worker 轮询任务。

所以它的推进方式和 publish task 一样，会受调度模式影响：

- 默认模式下，靠本地 `@Scheduled`
- `xxl-job` profile 下，靠 `workflowOutboxRelayJob`

如果 relay 明明开了但不推进，除了 RabbitMQ 本身，还要看调度是否真的在触发。

## 本地联调建议

不要一开始就同时打开所有东西。

更稳的顺序是：

1. 先只跑 MySQL，确认主业务接口能走通
2. 再加 `demo`，确认能登录拿 token
3. 再加 `rabbitmq`
4. 再打开 relay
5. 最后再决定要不要把 consumer 和 XXL-Job 一起带上

## 相关文档

- `docs/OPERATIONS.md`
- `docs/TESTING.md`

# Outbox + RabbitMQ 基础设施闭环

本项目采用 **Outbox Pattern** 实现“可靠投递”：

- 业务代码只负责把领域事件写入 outbox 表（同事务落库）
- relay worker 负责扫描 outbox，把消息投递到 RabbitMQ，并做重试/死信

这样可以避免常见的不一致问题：

- DB 已提交，但 MQ 发送失败
- MQ 已发送，但 DB 回滚

## 核心模块与代码位置

- 事件发布扩展点：`src/main/java/com/contentworkflow/common/messaging/WorkflowEventPublisher.java`
- Outbox publisher（只写 DB outbox，不直接发 MQ）：
  - `src/main/java/com/contentworkflow/common/messaging/outbox/OutboxWorkflowEventPublisher.java`
  - `src/main/java/com/contentworkflow/common/messaging/outbox/JpaOutboxEnqueuer.java`
- Outbox 表模型与查询：
  - `src/main/java/com/contentworkflow/common/messaging/outbox/OutboxEventEntity.java`
  - `src/main/java/com/contentworkflow/common/messaging/outbox/OutboxEventRepository.java`
- Relay worker（闭环：claim -> send -> ack/retry/dead）：
  - `src/main/java/com/contentworkflow/workflow/application/task/OutboxRelayWorker.java`
- 配置项：
  - `src/main/java/com/contentworkflow/common/messaging/WorkflowMessagingProperties.java`
  - `src/main/java/com/contentworkflow/common/messaging/WorkflowMessagingConfiguration.java`

## 事件投递流程（闭环）

1. 业务侧调用 `WorkflowEventPublisher.publish(event)`。
2. 若 `workflow.outbox.enabled=true`，publisher 会把事件写入 `workflow_outbox_event`。
3. 若 `workflow.outbox.relay.enabled=true`，`OutboxRelayWorker` 轮询 outbox：
   - 事务内 claim：悲观锁读取可投递记录，置为 `SENDING` 并落锁（lockedBy/lockedAt）
   - 事务外 send：调用 `RabbitTemplate.send(exchange, routingKey, message)`
   - 事务内 ack：成功置 `SENT`；失败置 `FAILED` 并计算 `nextRetryAt`；超过阈值置 `DEAD`

关键点：**不在同一个 DB 事务里直接发 MQ**，避免事务回滚导致重复投递。

## RabbitMQ 消息格式约定

消息体：
- JSON 字符串（来源：outbox 的 `payload_json`）

消息头：
- `messageId`：outbox 的 `event_id`
- `x-event-type`：事件类型（如 `CONTENT_PUBLISHED`）
- `x-aggregate-type`：聚合类型
- `x-aggregate-id`：聚合 id
- `x-aggregate-version`：聚合版本（可选）
- 以及 `headers_json` 中的自定义头（尽力解析，解析失败不影响投递）

## 如何启用（只配置，不执行 SQL）

1. 配置 RabbitMQ 连接参数（Spring Boot 标准配置）：

```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
    virtual-host: /
```

2. 打开 outbox 写入和 relay：

```yaml
workflow:
  outbox:
    enabled: true
    exchange: cpw.workflow.events
    routingKeyPrefix: content.
    relay:
      enabled: true
      pollDelayMs: 1000
      batchSize: 50
      lockSeconds: 60
      maxRetries: 10
      baseDelaySeconds: 5
```

## 建表 SQL（参考）

说明：这里给出 MySQL 参考 DDL，你可以合并到自己的迁移脚本中。

```sql
CREATE TABLE IF NOT EXISTS workflow_outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  aggregate_version INT NULL,
  exchange_name VARCHAR(128) NOT NULL,
  routing_key VARCHAR(256) NOT NULL,
  payload_json TEXT NOT NULL,
  headers_json TEXT NULL,
  status VARCHAR(16) NOT NULL,
  attempt INT NOT NULL,
  next_retry_at DATETIME NULL,
  locked_by VARCHAR(64) NULL,
  locked_at DATETIME NULL,
  error_message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL,
  sent_at DATETIME NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_outbox_event_id (event_id),
  KEY idx_outbox_status_next (status, next_retry_at, created_at),
  KEY idx_outbox_locked (locked_at),
  KEY idx_outbox_aggregate (aggregate_type, aggregate_id)
);
```

## 使用建议（面试可讲）

- Outbox 把“业务提交”和“消息投递”解耦：业务侧只关心写 DB，投递交给 relay 闭环。
- relay 做到了可观测的状态机：`NEW/FAILED -> SENDING -> SENT/DEAD`，并且有指数退避重试。
- 多实例场景用悲观锁 + 过期锁回收，减少重复 claim 和僵尸锁。


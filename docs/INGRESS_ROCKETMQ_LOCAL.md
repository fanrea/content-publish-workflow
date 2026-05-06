# Ingress RocketMQ Local Guide

本指南覆盖 `workflow.ingress.rocketmq.*` 本地联调，聚焦协同写入主链路。

## 1. 主链路口径

当前默认叙述：

- RocketMQ ingress 是协同写入主链路。
- ACK 代表入口接收，不代表全局生效。
- `OP_APPLIED` 代表进入服务端顺序并生效。

非主链路说明：

- RabbitMQ/outbox 不是当前仓库默认主链路，不作为默认架构口径。

## 2. 启动依赖

```bash
docker compose -f compose.local.yml up -d mysql redis
docker compose -f compose.local.yml --profile rocketmq up -d rocketmq-namesrv rocketmq-broker
```

## 3. 启动应用

```powershell
$env:DOC_INGRESS_ROCKETMQ_ENABLED="true"
$env:INGRESS_ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
mvn spring-boot:run
```

## 4. 最小验证

1. WS 发送 `EDIT_OP`，应先收到 `ACK(accepted_by_ingress)`。
2. 消费成功后应收到 `OP_APPLIED(revision=...)`。
3. 使用落后 `baseRevision` 发送 `SYNC_OPS`，应收到 `SYNC_DONE(latestRevision=...)`。

## 5. 故障补偿说明

推荐面试/项目讲解口径：

- RocketMQ 顺序消息
- DB 唯一键幂等（`documentId + sessionId + clientSeq`）
- operation log 可重放补偿（`SYNC_OPS`）

这也是当前实现方向，不依赖 RabbitMQ/outbox 作为核心路径。

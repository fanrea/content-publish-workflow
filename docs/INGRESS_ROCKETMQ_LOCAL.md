# Ingress RocketMQ Local Guide

本指南只覆盖文档编辑入口流（`workflow.ingress.rocketmq.*`）的本地联调，不涉及业务代码改动。

## 1. 启动依赖

先启动 MySQL / Redis：

```bash
docker compose -f compose.local.yml up -d mysql redis
```

再启动 RocketMQ（`namesrv` + `broker`）：

```bash
docker compose -f compose.local.yml --profile rocketmq up -d rocketmq-namesrv rocketmq-broker
```

## 2. 启用 ingress 配置

`application.yml` 默认值：

- `workflow.ingress.rocketmq.enabled=false`
- `workflow.ingress.rocketmq.name-server=127.0.0.1:9876`（可覆盖）

本地会话启用方式（PowerShell）：

```powershell
$env:DOC_INGRESS_ROCKETMQ_ENABLED="true"
$env:INGRESS_ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
mvn spring-boot:run
```

## 3. 最小验证

1. 应用启动日志中不应出现以下报错：
   - `workflow.ingress.rocketmq.name-server must be configured...`
2. 建立 WS 会话并发送 `EDIT_OP` 后，应用日志应出现 ingress 发布/消费相关日志。
3. 可选：查看 RocketMQ 容器日志确认 broker 与 namesrv 已连接。

## 4. 关闭与清理

```bash
docker compose -f compose.local.yml --profile rocketmq down
```

如需连数据卷一起删除：

```bash
docker compose -f compose.local.yml --profile rocketmq down -v
```

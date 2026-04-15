# 内容发布工作流服务

一个面向内容管理后台的后端服务，聚焦内容草稿、审核、发布、版本快照、回滚、差异发布、异步副作用编排与人工恢复能力。

项目目标不是做一个“大而全”的内容平台，而是把“内容发布工作流”这条核心链路做扎实，体现真实业务系统中的状态机、最终一致性、任务编排、审计追踪与故障恢复设计。

---

## 1. 项目简介

本项目是一个独立的内容发布工作流域服务，采用前后端分离模式，仅提供 HTTP JSON REST API，不负责页面渲染、登录态维护或真实外部下游系统实现。

服务职责包括：

- 草稿创建、编辑、详情查询、分页筛选、统计
- 审核提交流程、审核通过、审核驳回、审核记录追踪
- 发布状态机推进
- 发布快照保存与历史版本追踪
- 基于历史快照的一键回滚
- 内容下线
- 发布差异识别与副作用任务编排
- Outbox + RabbitMQ 异步消息链路
- 发布任务重试、死信、人工恢复
- 结构化审计日志与发布时间线追踪

---

## 2. 技术栈

### 基础框架
- Java 17
- Spring Boot 3.2.5
- Spring Web
- Spring Validation

### 持久化
- Spring Data JPA
- MySQL
- H2（本地演示 / 测试）

### 缓存
- Spring Cache
- Redis
- Lettuce
- commons-pool2

### 消息与异步
- RabbitMQ
- Spring AMQP
- Outbox Pattern

### 调度与运维
- XXL-Job Executor
- Spring Boot Actuator
- Micrometer + Prometheus

### 文档与开发支持
- SpringDoc OpenAPI / Swagger UI
- Lombok

---

## 3. 当前已实现的核心能力

### 3.1 草稿管理
已支持：

- 创建草稿
- 更新草稿
- 草稿详情查询
- 草稿分页查询
- 关键字 / 状态 / 时间范围筛选
- 草稿状态统计

### 3.2 审核流
已支持：

- 提交审核
- 审核通过
- 审核驳回
- 审核记录查询

### 3.3 发布主链路
发布流程已经统一为：

- 发布接口只负责把状态推进到 `PUBLISHING`
- 在主事务内完成数据库写入：
  - 草稿状态推进
  - 快照保存
  - 发布任务创建
  - 发布日志记录
- 最终由异步 worker 推进到：
  - `PUBLISHED`
  - `PUBLISH_FAILED`

这意味着发布接口本身不直接执行真实副作用，而是负责“可靠落库 + 状态推进”。

### 3.4 版本快照与回滚
已支持：

- 每次发布保存内容快照
- 历史版本追踪
- 基于历史快照的一键回滚
- 回滚复用发布任务编排，而不是简单覆盖正文

### 3.5 内容下线
已支持：

- 已发布内容下线
- 下线日志记录

### 3.6 差异发布
已支持：

- 展示草稿与已发布版本的 diff
- 根据变更范围决定是否需要执行副作用任务

当前差异识别维度包括：

- 首次发布
- 格式调整
- 元数据变更
- 正文变更

可基于差异决定是否触发：

- 搜索刷新
- 读模型同步
- 通知分发

### 3.7 发布任务编排
已支持：

- 发布任务表
- 任务状态管理
- 失败重试
- 死信
- 第一版补偿逻辑
- 当前版本任务人工重试
- 单任务人工恢复

### 3.8 Outbox + RabbitMQ 异步链路
已支持：

- Outbox 模式落库
- RabbitMQ relay worker
- exchange / queue / binding 声明
- 消费者监听入口
- 消费防重
- 消费审计日志

当前消费侧已经完成工程接缝，但真实搜索、读模型、通知系统仍为 stub。

### 3.9 人工恢复闭环
已支持：

- 查询可恢复发布任务
- 单任务人工重试
- 当前发布版本批量人工重试
- 查询失败 / 死信 outbox 事件
- outbox 事件人工重试
- 只允许恢复当前 `publishedVersion` 对应任务，避免误恢复历史版本

### 3.10 统一审计与时间线
已支持结构化审计字段：

- `traceId`
- `requestId`
- `operatorId`
- `operatorName`
- `targetType`
- `targetId`
- `publishedVersion`
- `taskId`
- `outboxEventId`
- `beforeStatus`
- `afterStatus`
- `result`
- `errorCode`
- `errorMessage`

并提供：

- 草稿维度审计日志查询
- `traceId` 维度全链路时间线查询
- `publishedVersion` 维度聚合时间线查询

---

## 4. 核心设计亮点

### 4.1 发布接口不直接做副作用
发布接口只做两件事：

1. 主事务内可靠写库
2. 把状态推进到 `PUBLISHING`

真正的副作用由异步任务执行，最终再由 worker 推进到终态。

这样可以避免：

- 接口执行时间过长
- 发布请求和副作用执行强耦合
- 失败难恢复
- 状态不一致难追踪

### 4.2 Outbox 保证最终一致性
业务事务中只写数据库和 outbox，不直接发 MQ。  
由 relay worker 轮询 outbox 投递 RabbitMQ，实现：

- 业务提交与消息发送解耦
- 避免 DB 成功、MQ 失败的不一致
- 支持失败重试、死信、人工恢复

### 4.3 差异驱动的任务编排
不是每次发布都执行完整副作用，而是根据差异决定任务集合。  
这样能更接近真实生产系统中的增量发布思路。

### 4.4 结构化审计追踪
项目不只是简单打日志，而是把一次发布涉及的：

- 用户发起
- 审核动作
- 发布请求
- 任务执行
- MQ 消费
- 失败补偿
- 人工恢复

统一收敛为结构化审计链路，支持完整时间线追踪。

### 4.5 人工恢复能力
当任务或 outbox 进入失败 / 死信状态时，不需要人工改库恢复，而是可以通过 API 做受控重试，体现面向生产故障的恢复设计。

---

## 5. 项目架构分层

```text
src/main/java/com/contentworkflow
├─ common          # 通用响应、异常、认证、消息基础设施
├─ workflow
│  ├─ interfaces   # Controller / DTO / VO
│  ├─ application  # 应用服务、任务编排、worker
│  ├─ domain       # 实体、枚举、领域规则
│  └─ infrastructure # JPA 实体、Repository、持久化适配
```

分层职责：

- `interfaces`：提供 REST API，处理请求响应模型
- `application`：编排业务流程、状态推进、任务执行
- `domain`：承载领域对象与状态定义
- `infrastructure`：JPA、MySQL、Redis、RabbitMQ 等基础设施适配
- `common`：通用能力沉淀

---

## 6. 关键状态与流程

### 6.1 草稿状态
典型状态包括：

- `DRAFT`
- `UNDER_REVIEW`
- `APPROVED`
- `REJECTED`
- `PUBLISHING`
- `PUBLISHED`
- `PUBLISH_FAILED`
- `OFFLINE`

### 6.2 一次典型发布流程

```text
用户发起发布
  -> 服务校验状态与幂等
  -> 主事务内写入：
     - draft 状态 = PUBLISHING
     - snapshot
     - publish tasks
     - publish logs
     - outbox event
  -> worker 异步执行任务
  -> 任务全部成功 => draft = PUBLISHED
  -> 任务出现不可恢复失败 => draft = PUBLISH_FAILED
```

---

## 7. 典型 API 能力

### 草稿
- `POST /api/workflows/drafts`
- `PUT /api/workflows/drafts/{draftId}`
- `GET /api/workflows/drafts/{draftId}`
- `GET /api/workflows/drafts/page`
- `GET /api/workflows/drafts/stats`

### 审核
- `POST /api/workflows/drafts/{draftId}/submit-review`
- `POST /api/workflows/drafts/{draftId}/review`
- `GET /api/workflows/drafts/{draftId}/reviews`

### 发布与版本
- `POST /api/workflows/drafts/{draftId}/publish`
- `POST /api/workflows/drafts/{draftId}/rollback`
- `POST /api/workflows/drafts/{draftId}/offline`
- `GET /api/workflows/drafts/{draftId}/snapshots`
- `GET /api/workflows/drafts/{draftId}/publish-diff`

### 任务与命令
- `GET /api/workflows/drafts/{draftId}/tasks`
- `GET /api/workflows/drafts/{draftId}/commands`

### 审计日志
- `GET /api/workflows/drafts/{draftId}/logs`
- `GET /api/workflows/drafts/{draftId}/logs/timeline?traceId=...`
- `GET /api/workflows/drafts/{draftId}/logs/publish-timeline?publishedVersion=...`

### 人工恢复
- `GET /api/workflows/drafts/{draftId}/recovery/tasks`
- `POST /api/workflows/drafts/{draftId}/tasks/{taskId}/manual-retry`
- `POST /api/workflows/drafts/{draftId}/tasks/manual-retry-current-version`
- `GET /api/workflows/outbox/events/recovery`
- `POST /api/workflows/outbox/events/{outboxEventId}/manual-retry`

---

## 8. 运行方式

### 8.1 环境要求
- JDK 17+
- MySQL 8.x
- Redis
- RabbitMQ
- XXL-Job Admin（如需调度演示）

### 8.2 默认模式
默认可以使用 H2 进行本地演示，不强依赖 Redis。

### 8.3 常见 Profile
- 默认：H2，本地演示
- `mysql`：切换 MySQL
- `redis`：启用 Redis Cache
- `rabbitmq`：启用 RabbitMQ 配置
- `ops`：暴露更多 Actuator / Prometheus 端点
- `loadtest`：压测配置

### 8.4 常见环境变量
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `RABBIT_HOST`
- `RABBIT_PORT`
- `RABBIT_USER`
- `RABBIT_PASSWORD`
- `SERVER_PORT`
- `MANAGEMENT_PORT`

---

## 9. 可观测性与运维接口

已接入：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/metrics`
- `/actuator/prometheus`

并支持：

- outbox relay 轮询
- 发布任务轮询
- 死信任务扫描
- 死信 outbox 扫描
- XXL-Job 调度接入

---

## 10. 当前项目的面试价值

这个项目的价值不在于“接口数量多”，而在于它已经覆盖了真实后端系统中比较有代表性的几个点：

- 工作流状态机
- 审核与发布解耦
- 快照版本化
- 回滚设计
- 差异发布
- 任务编排
- Outbox 最终一致性
- MQ 消费防重
- 审计链路追踪
- 人工恢复闭环

如果用于简历/面试，可以概括为：

> 实现了一个基于 Spring Boot 的内容发布工作流服务，围绕草稿、审核、发布、回滚构建状态机与版本快照模型，并基于 Outbox + RabbitMQ 实现异步副作用链路、消费防重、失败重试、死信与人工恢复，同时通过结构化审计日志支持发布全链路追踪。

---

## 11. 当前未完全收口的部分

以下内容已经有基础，但还不是最终完成态：

- 权限模型仍是轻量版，请求头角色控制，未实现完整 RBAC
- 审计体系已结构化，但还可以继续做统一审计上下文抽象
- 搜索索引、读模型、通知服务仍为 stub
- 压测与指标报告未完全补齐
- 部分文档仍需继续回补

---

## 12. 后续规划

下一阶段优先方向：

1. 完善权限模型
2. 继续增强统一审计体系
3. 补更多人工补偿/恢复入口
4. 接入真实外部下游系统
5. 补齐压测与指标报告
6. 完善文档

---

## 13. 文档索引

- `docs/SERVICE_ARCHITECTURE.md`：服务架构说明
- `docs/OPERATIONS.md`：运行与运维说明
- `docs/OUTBOX_RABBITMQ.md`：Outbox + RabbitMQ 设计
- `docs/API_DESIGN.md`：接口设计说明
- `docs/DOMAIN_MODEL.md`：领域模型说明
- `docs/WORKFLOW_INVARIANTS.md`：工作流不变量
- `docs/OBSERVABILITY.md`：可观测性说明
- `docs/LOAD_TEST.md`：压测说明
- `docs/FRONTEND_INTEGRATION.md`：前端联调说明

---

## 14. 一句话总结

当前项目已经完成内容发布工作流的核心业务主链路和大部分工程能力，包括状态机、版本快照、回滚、差异发布、发布任务编排、Outbox + RabbitMQ 异步副作用链路、消费防重、消费审计以及 XXL-Job 调度接入；后续重点继续补权限模型、更完整审计体系、真实下游集成，以及压测与指标收尾。

# 内容发布工作流服务

这是一个围绕“草稿 -> 审核 -> 发布 -> 回滚 -> 下线 -> 恢复”完整链路设计的后端服务。项目重点不在于堆接口数量，而在于把工作流状态机、版本快照、异步副作用、最终一致性、人工恢复和审计追踪这些真实生产系统里常见的问题做成一个可运行、可讲清楚、可继续扩展的实现。

项目当前使用 Java 17、Spring Boot 3、Spring Data JPA、MySQL/H2、Redis、RabbitMQ、Actuator、XXL-Job。默认模式下可以只依赖 H2 启动；需要更接近生产的演示时，再按需打开 `mysql`、`redis`、`rabbitmq`、`ops`、`loadtest`、`xxl-job` 等 profile。

## 一、项目解决什么问题

很多内容系统的“发布”并不是一次简单的数据库更新，而是一组有顺序、有状态、有副作用的业务动作：

- 编辑先创建和修改草稿
- 草稿进入审核流
- 审核通过后才能发起发布
- 发布时不仅要保存最终内容，还要触发搜索刷新、读模型同步、通知分发等副作用
- 任一步骤失败后，需要支持重试、补偿和人工恢复
- 运维和研发还需要知道一次发布到底发生了什么，问题出在哪一环

这个项目就是把这条链路拆清楚、落到代码和表结构里。

## 二、当前已经具备的能力

### 1. 草稿能力

- 创建草稿、更新草稿、查询详情
- 草稿分页筛选
- 草稿摘要接口
- 草稿状态统计接口
- 草稿列表缓存与统计缓存

### 2. 审核能力

- 编辑提交审核
- 审核人审批通过或驳回
- 审核记录留痕
- 驳回意见回写到草稿

### 3. 发布主链路

- 发布前校验工作流状态
- 支持 `Idempotency-Key` 幂等发布
- 每次发布生成不可变快照
- 根据内容差异生成副作用任务
- 发布请求写入发布命令、任务、日志
- 草稿状态进入 `PUBLISHING`
- 由异步 worker 推动到 `PUBLISHED` 或 `PUBLISH_FAILED`

### 4. 版本与回滚

- 每次成功发布都对应一个 `publishedVersion`
- 快照不可变
- 回滚不会覆盖旧版本，而是基于历史快照再生成一个新的发布版本
- 支持查看快照历史和发布差异

### 5. 异步副作用与最终一致性

- 发布任务表负责执行副作用
- 内置三类任务：
  - 刷新搜索索引
  - 同步下游读模型
  - 发送发布通知
- 任务支持领取、执行、失败重试、死信
- Outbox 表负责在主事务内可靠落库事件
- Relay worker 负责把 outbox 事件异步投递到 RabbitMQ
- 消费端带有去重保护和审计记录

### 6. 人工恢复

- 查看当前草稿下可恢复的失败任务
- 对单个失败任务手动重试
- 对当前发布版本的失败任务批量重试
- 查看失败或死信的 outbox 事件
- 对单个 outbox 事件手动重试
- 只允许恢复当前 `publishedVersion` 的任务，避免误操作旧版本

### 7. 审计与时间线

- 审计日志记录操作人、目标对象、前后状态、错误码、错误信息
- 支持按草稿查看日志
- 支持按 `traceId` 查看一次链路时间线
- 支持按 `publishedVersion` 查看聚合后的发布时间线

## 三、项目整体结构

```text
src/main/java/com/contentworkflow
├─ common
│  ├─ api            统一响应模型
│  ├─ cache          缓存配置、缓存名称、缓存键
│  ├─ exception      业务异常
│  ├─ messaging      事件、Outbox、RabbitMQ、消费防重
│  ├─ scheduler      XXL-Job 配置
│  └─ web/auth       轻量权限模型、操作人解析、拦截器
└─ workflow
   ├─ interfaces     Controller、DTO、VO
   ├─ application    应用服务、任务 worker、恢复服务、对账服务
   ├─ domain         领域实体与枚举
   └─ infrastructure JPA 实体、Repository、持久化映射
```

各层职责非常明确：

- `interfaces` 只负责接收 HTTP 请求、参数校验和返回结果
- `application` 负责编排业务流程和状态推进
- `domain` 负责承载领域对象和状态定义
- `infrastructure` 负责把领域对象落到数据库
- `common` 放通用基础设施，例如缓存、消息、鉴权、异常处理

## 四、核心设计思路

### 1. 发布接口不直接执行副作用

发布接口只做主事务内的可靠写入：

- 草稿状态进入 `PUBLISHING`
- 保存快照
- 创建副作用任务
- 记录发布命令
- 记录审计日志
- 可选写入 outbox 事件

真正的副作用由后台 worker 异步执行。这样做有几个直接好处：

- 接口响应更稳定
- 发布过程更容易恢复
- 能把失败控制在具体任务上
- 更容易实现幂等和审计

### 2. 快照不可变

项目把“草稿”和“已发布内容”明确拆成两个概念：

- `content_draft` 是当前可编辑工作副本
- `content_publish_snapshot` 是已经对外发布的不可变版本

这意味着：

- 修改草稿不会改历史发布结果
- 回滚本质上是“拿旧快照重新发一次”
- 审计和排障时能知道某个线上版本到底是哪次发布产出的

### 3. 任务与主业务分离

发布涉及的副作用被建模为 `content_publish_task`。任务表记录：

- 任务类型
- 当前状态
- 重试次数
- 错误信息
- 下次重试时间
- 锁定 worker 信息

这样做的结果是：

- 失败可重试
- 并发 worker 可安全领取任务
- 能单独恢复某个失败任务
- 能区分“主事务成功”和“副作用还没完成”

### 4. Outbox 保证最终一致性

项目实现了典型的 Outbox 模式：

- 主业务事务中只负责写数据库和 outbox 表
- 不在主事务里直接发 MQ
- 由 outbox relay worker 后续轮询投递到 RabbitMQ

这比“事务里直接发 MQ”更适合做可靠交付，因为它能避免：

- 数据已提交但消息没发出去
- 消息发出去了但数据库事务回滚
- MQ 瞬时不可用导致主链路失败

### 5. 恢复能力是内建的，不是事后补的

当前项目不是“失败了手改数据库”，而是明确提供恢复接口：

- 恢复发布任务
- 恢复 outbox 事件
- 批量恢复当前发布版本
- 恢复时自动补审计日志

这让项目更像一个可运维的系统，而不是只在 happy path 上能跑通的 demo。

## 五、状态机说明

草稿工作流状态如下：

- `DRAFT`：草稿中
- `REVIEWING`：审核中
- `REJECTED`：审核驳回
- `APPROVED`：审核通过
- `PUBLISHING`：发布中
- `PUBLISHED`：已发布
- `PUBLISH_FAILED`：发布失败
- `OFFLINE`：已下线

常见流转路径如下：

```text
DRAFT
  -> 提交审核
REVIEWING
  -> 审核通过 -> APPROVED
  -> 审核驳回 -> REJECTED
REJECTED
  -> 编辑后回到 DRAFT
APPROVED
  -> 发起发布 -> PUBLISHING
PUBLISHING
  -> 任务全部成功 -> PUBLISHED
  -> 任务不可恢复失败 -> PUBLISH_FAILED
PUBLISHED
  -> 回滚 -> PUBLISHING（产生新版本）
  -> 下线 -> OFFLINE
PUBLISH_FAILED
  -> 人工恢复任务 -> PUBLISHING
OFFLINE
  -> 允许继续编辑或再次发起工作流
```

## 六、典型发布过程

一次正常发布大致会经历这些步骤：

1. 运营或操作人调用发布接口。
2. 服务检查草稿状态和幂等键。
3. 主事务内完成：
   - 草稿状态改为 `PUBLISHING`
   - 创建新的快照
   - 计算发布差异
   - 生成副作用任务
   - 保存发布命令
   - 写入审计日志
   - 如果启用 outbox，则写入 outbox 事件
4. `PublishTaskWorker` 异步领取任务并执行。
5. 任务全部成功后，草稿状态推进到 `PUBLISHED`。
6. 如果任务重试后仍失败，草稿状态进入 `PUBLISH_FAILED`。
7. 运维或操作人可以通过恢复接口把失败任务重新置回 `PENDING`。

## 七、对外接口概览

基础前缀：`/api/workflows`

### 草稿相关

- `GET /drafts`
- `GET /drafts/page`
- `GET /drafts/{draftId}/summary`
- `GET /drafts/stats`
- `POST /drafts`
- `PUT /drafts/{draftId}`
- `GET /drafts/{draftId}`

### 审核相关

- `POST /drafts/{draftId}/submit-review`
- `POST /drafts/{draftId}/review`
- `GET /drafts/{draftId}/reviews`

### 发布相关

- `POST /drafts/{draftId}/publish`
- `POST /drafts/{draftId}/rollback`
- `GET /drafts/{draftId}/publish-diff`
- `GET /drafts/{draftId}/snapshots`
- `POST /drafts/{draftId}/offline`
- `GET /drafts/{draftId}/tasks`
- `GET /drafts/{draftId}/commands`
- `GET /drafts/{draftId}/logs`
- `GET /drafts/{draftId}/logs/timeline`
- `GET /drafts/{draftId}/logs/publish-timeline`

### 恢复相关

- `GET /drafts/{draftId}/recovery/tasks`
- `POST /drafts/{draftId}/tasks/{taskId}/manual-retry`
- `POST /drafts/{draftId}/tasks/manual-retry-current-version`
- `POST /drafts/{draftId}/tasks/{taskId}/manual-requeue`
- `GET /outbox/events/recovery`
- `POST /outbox/events/{outboxEventId}/manual-retry`
- `POST /outbox/events/{outboxEventId}/manual-requeue`

更详细的接口说明见 [docs/API_DESIGN.md](/D:/java/content-publish-workflow/docs/API_DESIGN.md)。

## 八、权限模型

项目实现的是一套轻量但完整的请求头权限模型，不是完整的账号系统。

角色：

- `EDITOR`
- `REVIEWER`
- `OPERATOR`
- `ADMIN`

请求头主要有：

- `X-Workflow-Role`
- `X-Workflow-Operator-Id`
- `X-Workflow-Operator-Name`
- `X-Request-Id`

权限粒度已经细化到草稿读写、审核、发布、查看任务、查看命令、查看日志、人工恢复和 outbox 恢复，不再只是“按角色粗放拦截”。

## 九、运行方式

### 1. 默认最小启动

默认使用 H2 内存库，并自动执行 `src/main/resources/sql/schema.sql`。这种模式适合：

- 本地查看接口
- 跑单元测试和持久化测试
- 不依赖 MySQL、Redis、RabbitMQ 的快速演示

### 2. 使用 MySQL

启用 `mysql` profile 后：

- 数据源切到 MySQL
- `ddl-auto=validate`
- 不再自动执行建表脚本
- 需要先手动执行仓库根目录的 `sql/schema.sql`

### 3. 使用 Redis

启用 `redis` profile 后：

- Spring Cache 使用 Redis
- 就绪探针会把 Redis 作为依赖
- 草稿详情、列表、状态统计等缓存使用可配置 TTL

### 4. 使用 RabbitMQ

启用 `rabbitmq` profile 后：

- 开启 outbox 配置样例
- 可以声明交换机、队列和绑定
- relay 与 consumer 默认仍是关闭的，需要显式打开

### 5. 运维与压测

- `ops`：暴露更多 Actuator 端点，并建议使用独立管理端口
- `loadtest`：调整 Tomcat 和监控参数，适合做压测演示
- `xxl-job`：通过 XXL-Job 调度 worker 和扫描任务

详细运行说明见 [docs/OPERATIONS.md](/D:/java/content-publish-workflow/docs/OPERATIONS.md)。

## 十、数据库与缓存

核心表如下：

- `content_draft`
- `content_review_record`
- `content_publish_snapshot`
- `content_publish_task`
- `content_publish_command`
- `content_publish_log`
- `workflow_outbox_event`

它们的职责不是简单分表，而是分别承载：

- 当前工作副本
- 审核历史
- 不可变发布版本
- 副作用任务
- 发布幂等命令
- 审计日志
- 可靠消息中转

缓存层目前重点优化的是读请求，而不是把整个业务做成“缓存优先”系统：

- 草稿详情缓存
- 草稿列表缓存
- 草稿状态统计缓存
- 消费防重缓存
- 死信扫描标记缓存

## 十一、可观测性与运维

项目已经接入：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/metrics`
- `/actuator/prometheus`

启用 `ops` 后还可以看到更多运维端点，并可以配合：

- `docs/observability/prometheus.yml`
- `docs/observability/grafana-dashboard.json`

用来演示服务监控、请求延迟、JVM 指标和线程池指标。

## 十二、测试体系

测试分成两类：

- 以工作流规则为中心的应用层测试
- 以 JPA 持久化契约为中心的仓储层测试

测试重点覆盖：

- 状态机流转
- 错误码稳定性
- 快照与版本语义
- 幂等发布
- 发布任务和恢复逻辑
- 持久化映射

详细说明见 [docs/TESTING.md](/D:/java/content-publish-workflow/docs/TESTING.md)。

## 十三、适合怎么讲这个项目

如果这是你的课程项目、练手项目或面试项目，可以这样概括：

“我实现了一个内容发布工作流后端服务，把草稿、审核、发布、版本快照、回滚、下线、任务编排、Outbox 最终一致性、人工恢复和结构化审计整合到同一个系统里。发布接口本身不直接做副作用，而是用任务和 outbox 把主事务与异步链路拆开，支持失败重试、死信和人工恢复。”

## 十四、文档索引

- [docs/SERVICE_ARCHITECTURE.md](/D:/java/content-publish-workflow/docs/SERVICE_ARCHITECTURE.md)：系统分层、核心流程和组件关系
- [docs/API_DESIGN.md](/D:/java/content-publish-workflow/docs/API_DESIGN.md)：接口、请求头、权限和典型调用方式
- [docs/DOMAIN_MODEL.md](/D:/java/content-publish-workflow/docs/DOMAIN_MODEL.md)：领域模型、状态机、版本语义
- [docs/WORKFLOW_INVARIANTS.md](/D:/java/content-publish-workflow/docs/WORKFLOW_INVARIANTS.md)：工作流不变量与约束
- [docs/TABLE_DESIGN.md](/D:/java/content-publish-workflow/docs/TABLE_DESIGN.md)：核心表设计
- [docs/SQL_GUIDE.md](/D:/java/content-publish-workflow/docs/SQL_GUIDE.md)：建表、导数和本地 SQL 使用说明
- [docs/OPERATIONS.md](/D:/java/content-publish-workflow/docs/OPERATIONS.md)：运行、配置、profile 和排障
- [docs/OUTBOX_RABBITMQ.md](/D:/java/content-publish-workflow/docs/OUTBOX_RABBITMQ.md)：Outbox 与 RabbitMQ 链路说明
- [docs/REDIS_CACHE.md](/D:/java/content-publish-workflow/docs/REDIS_CACHE.md)：缓存设计
- [docs/REDIS_GUIDE.md](/D:/java/content-publish-workflow/docs/REDIS_GUIDE.md)：Redis 启用方式与联调说明
- [docs/OBSERVABILITY.md](/D:/java/content-publish-workflow/docs/OBSERVABILITY.md)：监控指标与日志
- [docs/LOAD_TEST.md](/D:/java/content-publish-workflow/docs/LOAD_TEST.md)：压测资产和压测方法
- [docs/TESTING.md](/D:/java/content-publish-workflow/docs/TESTING.md)：测试说明
- [docs/FRONTEND_INTEGRATION.md](/D:/java/content-publish-workflow/docs/FRONTEND_INTEGRATION.md)：前后端联调说明
- [docs/ERROR_CODES.md](/D:/java/content-publish-workflow/docs/ERROR_CODES.md)：错误码说明
- [docs/PRD.md](/D:/java/content-publish-workflow/docs/PRD.md)：项目目标与范围
- [docs/ROADMAP.md](/D:/java/content-publish-workflow/docs/ROADMAP.md)：后续演进方向

# 接口设计说明

本项目对外提供的是一个纯后端 JSON API，基础路径为 `/api/workflows`。接口设计重点不在“接口长什么样”，而在于接口背后的工作流边界、权限边界和恢复边界是否清晰。

## 一、基础约定

### 1. 协议与路径

- 协议：HTTP
- 数据格式：JSON
- 基础路径：`/api/workflows`
- OpenAPI 文档：`/v3/api-docs`
- Swagger 页面：`/swagger-ui`

### 2. 统一返回结构

所有接口都返回 `ApiResponse<T>`，典型结构如下：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {},
  "timestamp": "2026-04-16T12:00:00"
}
```

约定如下：

- `success=true` 表示业务成功
- `success=false` 表示业务失败
- HTTP 状态码负责表达错误大类
- `code` 负责表达业务细分错误

### 3. 分页结构

分页接口返回 `PageResponse<T>`，包含：

- `items`
- `total`
- `pageNo`
- `pageSize`
- `totalPages`

项目里 `pageNo` 从 `1` 开始。

## 二、请求头约定

### 1. 鉴权与操作人请求头

项目当前使用轻量工作流权限模型，请求头主要包括：

- `X-Workflow-Role`
- `X-Workflow-Operator-Id`
- `X-Workflow-Operator-Name`
- `X-Request-Id`

其中：

- `X-Workflow-Role` 支持单个或多个角色
- `X-Workflow-Operator-Id` 和 `X-Workflow-Operator-Name` 用于审计日志
- `X-Request-Id` 用于链路追踪和日志关联

如果缺少必要请求头，会返回 `401`；如果角色不合法或权限不足，会返回 `403`。

### 2. 幂等请求头

发布接口支持幂等键：

- 请求头：`Idempotency-Key`
- 请求体字段：`idempotencyKey`

如果请求体没有传，控制器会自动读取请求头并补到请求对象里。

## 三、角色与权限

角色有四类：

- `EDITOR`
- `REVIEWER`
- `OPERATOR`
- `ADMIN`

权限已经细化到如下粒度：

- 草稿读取
- 草稿调试读取
- 草稿写入
- 草稿统计读取
- 提交审核
- 审核决策
- 发布差异读取
- 发布执行
- 回滚执行
- 下线执行
- 任务查看
- 发布命令查看
- 审计日志查看
- 发布任务人工恢复
- Outbox 人工恢复

这意味着文档和联调时不应该只说“这个接口谁能调”，而是要知道“为什么能调”。

## 四、接口分组

### 1. 草稿接口

#### `GET /drafts`

用途：

- 返回全量草稿明细
- 包含正文大字段
- 主要用于调试和管理员查看

权限：

- 角色：`ADMIN`
- 权限：`DRAFT_DEBUG_READ`

#### `GET /drafts/page`

用途：

- 列表页主接口
- 返回分页摘要，不返回正文大字段

权限：

- `DRAFT_READ`

支持的查询参数：

- `keyword`
- `status`
- `searchInBody`
- `pageNo`
- `pageSize`
- `sortBy`
- `sortDirection`
- `createdFrom`
- `createdTo`
- `updatedFrom`
- `updatedTo`

说明：

- `status` 支持多值
- `sortBy` 支持 `ID`、`CREATED_AT`、`UPDATED_AT`
- `sortDirection` 支持 `ASC`、`DESC`
- `searchInBody=false` 时，更适合列表页

#### `GET /drafts/{draftId}/summary`

用途：

- 返回单个草稿的工作流摘要
- 适合详情页顶部概览区

权限：

- `DRAFT_READ`

#### `GET /drafts/stats`

用途：

- 返回草稿状态统计
- 适合前端页签计数、状态徽标

权限：

- `DRAFT_STATS_READ`

#### `POST /drafts`

用途：

- 创建草稿

权限：

- `DRAFT_WRITE`

说明：

- 如果没有传 `bizNo`，服务层会生成默认业务号
- 创建动作会写审计日志

#### `PUT /drafts/{draftId}`

用途：

- 更新草稿

权限：

- `DRAFT_WRITE`

说明：

- 编辑会影响 `draftVersion`
- 编辑是否允许，受当前工作流状态约束

#### `GET /drafts/{draftId}`

用途：

- 返回草稿完整详情
- 包含正文内容

权限：

- `DRAFT_READ`

### 2. 审核接口

#### `POST /drafts/{draftId}/submit-review`

用途：

- 把草稿从可编辑阶段推进到审核阶段

权限：

- `REVIEW_SUBMIT`

#### `POST /drafts/{draftId}/review`

用途：

- 审核通过或驳回

权限：

- `REVIEW_DECIDE`

请求体重点字段：

- `decision`
- `comment`

说明：

- `decision=APPROVE` 时进入 `APPROVED`
- `decision=REJECT` 时进入 `REJECTED`
- 驳回意见会写入草稿和审核记录

#### `GET /drafts/{draftId}/reviews`

用途：

- 查看审核记录

权限：

- `DRAFT_READ`

### 3. 发布与版本接口

#### `POST /drafts/{draftId}/publish`

用途：

- 发起一次新的发布

权限：

- `PUBLISH_EXECUTE`

主行为：

- 校验状态和幂等
- 生成快照
- 创建发布任务
- 生成发布命令
- 记录日志
- 进入 `PUBLISHING`

说明：

- 该接口成功返回不等于副作用已经全部完成
- 最终状态由后台 worker 推进

#### `POST /drafts/{draftId}/rollback`

用途：

- 基于历史快照回滚

权限：

- `ROLLBACK_EXECUTE`

说明：

- 回滚不是覆盖旧版本
- 回滚会生成新的发布版本并复用发布编排链路

#### `GET /drafts/{draftId}/publish-diff`

用途：

- 对比当前草稿与指定已发布版本的差异

权限：

- `PUBLISH_DIFF_READ`

说明：

- 不传 `basePublishedVersion` 时，通常以当前已发布版本为基准
- 差异会影响后续任务生成范围

#### `GET /drafts/{draftId}/snapshots`

用途：

- 查看历史发布快照

权限：

- `DRAFT_READ`

#### `POST /drafts/{draftId}/offline`

用途：

- 将内容下线

权限：

- `OFFLINE_EXECUTE`

### 4. 发布任务、命令与审计接口

#### `GET /drafts/{draftId}/tasks`

用途：

- 查看该草稿关联的发布任务

权限：

- `TASK_VIEW`

#### `GET /drafts/{draftId}/commands`

用途：

- 查看发布命令
- 便于确认幂等发布是否复用了既有命令

权限：

- `COMMAND_VIEW`

#### `GET /drafts/{draftId}/logs`

用途：

- 查看该草稿下的结构化审计日志

权限：

- `LOG_VIEW`

#### `GET /drafts/{draftId}/logs/timeline`

用途：

- 按 `traceId` 拉取一条链路的时间线

权限：

- `LOG_VIEW`

#### `GET /drafts/{draftId}/logs/publish-timeline`

用途：

- 按 `publishedVersion` 聚合一条发布过程

权限：

- `LOG_VIEW`

说明：

- 更适合做前端发布时间线视图
- 比原始日志列表更接近业务视角

### 5. 恢复接口

#### `GET /drafts/{draftId}/recovery/tasks`

用途：

- 查看该草稿下可恢复的发布任务

权限：

- `TASK_VIEW`

支持状态过滤：

- `FAILED`
- `DEAD`

#### `POST /drafts/{draftId}/tasks/{taskId}/manual-retry`

用途：

- 手动重试单个失败任务

权限：

- `TASK_MANUAL_REQUEUE`

说明：

- 只允许恢复当前 `publishedVersion` 对应的任务
- 恢复后任务会回到 `PENDING`

#### `POST /drafts/{draftId}/tasks/manual-retry-current-version`

用途：

- 批量恢复当前发布版本的所有失败任务

权限：

- `TASK_MANUAL_REQUEUE`

#### `POST /drafts/{draftId}/tasks/{taskId}/manual-requeue`

用途：

- 兼容旧命名的任务重入队接口

权限：

- `TASK_MANUAL_REQUEUE`

#### `GET /outbox/events/recovery`

用途：

- 查看失败或死信的 outbox 事件

权限：

- 角色：`ADMIN`
- 权限：`OUTBOX_MANUAL_REQUEUE`

支持参数：

- `draftId`
- `status`
- `limit`

#### `POST /outbox/events/{outboxEventId}/manual-retry`

用途：

- 手动恢复单个 outbox 事件

权限：

- `ADMIN + OUTBOX_MANUAL_REQUEUE`

#### `POST /outbox/events/{outboxEventId}/manual-requeue`

用途：

- 兼容旧命名的 outbox 重入队接口

权限：

- `ADMIN + OUTBOX_MANUAL_REQUEUE`

## 五、典型调用顺序

### 1. 正常发布顺序

1. `POST /drafts`
2. `PUT /drafts/{draftId}`
3. `POST /drafts/{draftId}/submit-review`
4. `POST /drafts/{draftId}/review`
5. `GET /drafts/{draftId}/publish-diff`
6. `POST /drafts/{draftId}/publish`
7. `GET /drafts/{draftId}/tasks`
8. `GET /drafts/{draftId}/logs/publish-timeline`

### 2. 发布失败后的恢复顺序

1. `GET /drafts/{draftId}/recovery/tasks`
2. `POST /drafts/{draftId}/tasks/{taskId}/manual-retry`
3. 或 `POST /drafts/{draftId}/tasks/manual-retry-current-version`
4. 再次查看 `GET /drafts/{draftId}/logs/publish-timeline`

### 3. Outbox 恢复顺序

1. `GET /outbox/events/recovery`
2. `POST /outbox/events/{outboxEventId}/manual-retry`

## 六、联调建议

- 列表页只调 `/drafts/page`，不要用 `/drafts`
- 草稿页顶部优先调 `/drafts/{draftId}/summary`
- 状态统计页签优先调 `/drafts/stats`
- 发布后不要只看接口返回，要继续看任务和时间线接口
- 恢复接口属于运维能力，不建议给普通编辑角色开放

# 前后端联调说明

这份文档面向前端、测试和联调同学，重点不是解释后端内部实现，而是告诉你：应该调哪些接口、怎么传请求头、怎么处理发布后的异步状态。

## 一、基础信息

- 基础路径：`/api/workflows`
- 返回格式：统一 `ApiResponse<T>`
- 文档地址：`/swagger-ui`
- OpenAPI：`/v3/api-docs`

## 二、联调前必须知道的事

### 1. 这个项目不是同步发布模型

调用发布接口成功，只表示主事务已写入成功，并不代表副作用已经执行完成。

正确联调方式是：

1. 调发布接口
2. 再查任务接口
3. 再查发布时间线

### 2. 有角色权限

不同操作需要不同角色。最常见的联调组合是：

- 编辑接口：`EDITOR`
- 审核接口：`REVIEWER`
- 发布与恢复接口：`OPERATOR`
- Outbox 恢复接口：`ADMIN`

### 3. 列表接口和详情接口不要混用

- 列表页优先用 `/drafts/page`
- 详情页正文才用 `/drafts/{draftId}`
- 调试时才用 `/drafts`

## 三、请求头建议

联调时至少准备下面这些请求头：

```http
X-Workflow-Role: EDITOR
X-Workflow-Operator-Id: u1001
X-Workflow-Operator-Name: 张三
X-Request-Id: req-20260416-0001
```

发布接口如果要做重复点击保护，建议带上：

```http
Idempotency-Key: publish-draft-123-v1
```

## 四、页面级接口建议

### 1. 列表页

建议使用：

- `GET /drafts/page`
- `GET /drafts/stats`

这样可以同时拿到：

- 当前页摘要列表
- 各状态计数

不建议：

- 直接用 `GET /drafts` 做列表

原因：

- 会把正文大字段一起拉回来
- 只有管理员可访问

### 2. 草稿详情页

建议使用：

- `GET /drafts/{draftId}/summary`
- `GET /drafts/{draftId}`
- `GET /drafts/{draftId}/reviews`
- `GET /drafts/{draftId}/snapshots`

### 3. 发布详情页

建议使用：

- `GET /drafts/{draftId}/tasks`
- `GET /drafts/{draftId}/commands`
- `GET /drafts/{draftId}/logs/publish-timeline`

如果要做原始链路排查，再调：

- `GET /drafts/{draftId}/logs/timeline?traceId=...`

### 4. 恢复页

建议使用：

- `GET /drafts/{draftId}/recovery/tasks`
- `POST /drafts/{draftId}/tasks/{taskId}/manual-retry`
- `POST /drafts/{draftId}/tasks/manual-retry-current-version`

管理员页面额外可以用：

- `GET /outbox/events/recovery`
- `POST /outbox/events/{outboxEventId}/manual-retry`

## 五、时间字段和查询参数

建议统一使用 ISO-8601：

- `2026-04-16T12:00:00`

分页查询常用参数：

- `pageNo`
- `pageSize`
- `keyword`
- `status`
- `sortBy`
- `sortDirection`
- `createdFrom`
- `createdTo`
- `updatedFrom`
- `updatedTo`

## 六、错误处理建议

前端处理时建议同时看三层信息：

1. HTTP 状态码
2. `success`
3. `code`

推荐处理方式：

- `400`：提示参数有误
- `401`：提示缺少认证/角色请求头
- `403`：提示当前角色无权限
- `404`：提示数据不存在
- `409`：提示状态冲突，建议刷新数据
- `500`：走通用系统异常提示

尤其是下面这些业务错误码，前端最好单独识别：

- `INVALID_WORKFLOW_STATE`
- `STALE_PUBLISH_TASK`
- `NO_RECOVERABLE_PUBLISH_TASK`
- `INVALID_OUTBOX_STATUS`

## 七、发布页正确交互方式

发布页常见误区是：点击发布后，只依赖发布接口返回结果更新页面。

更合理的做法是：

1. 点击发布
2. 接口成功后，把草稿状态先展示为“发布中”
3. 轮询任务列表或发布时间线
4. 当全部任务成功时展示“已发布”
5. 当出现 `PUBLISH_FAILED` 时展示恢复入口

## 八、联调建议顺序

如果从零开始联调，推荐顺序如下：

1. 草稿创建、修改、详情
2. 草稿分页和状态统计
3. 提交审核、审核通过/驳回
4. 发布差异和快照列表
5. 发布、任务、命令、时间线
6. 下线和回滚
7. 人工恢复

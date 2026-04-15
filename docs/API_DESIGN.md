# API Design（前后端分离）

本服务是一个独立的后端 API 服务，不渲染页面，只接收 HTTP 请求并输出 JSON。

- Base path: `/api/workflows`
- Content-Type: `application/json`
- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui`

## 请求头约定

受保护的写接口使用轻量角色头：

- `X-Workflow-Role: EDITOR`
- `X-Workflow-Role: REVIEWER`
- `X-Workflow-Role: OPERATOR`
- `X-Workflow-Role: ADMIN`

当前规则：
- 受保护接口缺少该请求头时返回 `401 UNAUTHORIZED`
- 角色不存在或权限不足时返回 `403 FORBIDDEN`
- 只读接口暂时保持开放

## 统一返回结构

所有接口返回统一包裹（见 `com.contentworkflow.common.api.ApiResponse`）：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {},
  "timestamp": "2026-04-14T12:00:00"
}
```

说明：
- `success=true` 时，`data` 为业务返回。
- `success=false` 时，`data=null`，并通过 HTTP 状态码表达错误类型（详见 `docs/ERROR_CODES.md`）。

## 草稿（Draft）

列表页建议只使用“分页摘要”接口，避免拉取 `body` 大字段。

| Method | Path | 描述 |
| --- | --- | --- |
| GET | `/drafts/page` | 草稿分页查询（摘要，不含 body） |
| GET | `/drafts/stats` | 草稿状态统计（Tab/Badge） |
| GET | `/drafts/{draftId}/summary` | 单草稿工作流摘要（详情页信息卡） |
| GET | `/drafts/{draftId}` | 草稿详情（含 body） |
| GET | `/drafts` | 全量草稿列表（含 body，仅调试/演示） |
| POST | `/drafts` | 创建草稿 |
| PUT | `/drafts/{draftId}` | 更新草稿 |

### 草稿分页查询（列表页）

`GET /api/workflows/drafts/page`

查询参数（完整枚举见 `DraftQueryRequest`）：
- `keyword`：模糊匹配 `bizNo/title/summary`；是否搜索 `body` 由 `searchInBody` 决定
- `status`：可多选，例如 `status=DRAFT&status=APPROVED`
- `searchInBody`：默认 `false`（列表页不建议开）
- `pageNo`：从 1 开始
- `pageSize`：建议 <= 100（接口允许到 200）
- `sortBy`：`ID` / `CREATED_AT` / `UPDATED_AT`（默认 `UPDATED_AT`）
- `sortDirection`：`ASC` / `DESC`（默认 `DESC`）
- 时间范围：`createdFrom/createdTo/updatedFrom/updatedTo`，建议 ISO-8601，例如 `2026-04-14T12:00:00`

示例：

```http
GET /api/workflows/drafts/page?pageNo=1&pageSize=20&keyword=release&status=DRAFT&sortBy=UPDATED_AT&sortDirection=DESC
```

### 创建草稿

```http
POST /api/workflows/drafts
Content-Type: application/json
```

```json
{
  "bizNo": "COURSE-20260414-0001",
  "title": "Java 并发专题",
  "summary": "面向企业内部培训的并发课程",
  "body": "..."
}
```

## 审核（Review）

| Method | Path | 描述 |
| --- | --- | --- |
| POST | `/drafts/{draftId}/submit-review` | 提交审核 |
| POST | `/drafts/{draftId}/review` | 审核通过/驳回 |
| GET | `/drafts/{draftId}/reviews` | 审核记录列表 |

## 发布（Publish）

| Method | Path | 描述 |
| --- | --- | --- |
| POST | `/drafts/{draftId}/publish` | 发起发布 |
| POST | `/drafts/{draftId}/rollback` | 回滚到指定发布版本（基于快照生成新版本） |
| POST | `/drafts/{draftId}/offline` | 下线 |
| GET | `/drafts/{draftId}/snapshots` | 发布快照列表 |
| GET | `/drafts/{draftId}/commands` | 幂等发布命令列表 |
| GET | `/drafts/{draftId}/tasks` | 发布副作用任务列表 |
| GET | `/drafts/{draftId}/logs` | 发布日志（审计） |

## HTTP 状态码约定（简版）

- `200/201`：业务成功（`success=true`）
- `400`：参数校验失败、业务入参不合法（`VALIDATION_ERROR` 等）
- `404`：草稿/快照不存在（`DRAFT_NOT_FOUND` / `SNAPSHOT_NOT_FOUND`）
- `409`：工作流状态冲突（`INVALID_WORKFLOW_STATE`）
- `500`：未预期异常（`INTERNAL_ERROR`）

## 请求头约定（建议）

本服务可以部署在网关/Ingress 后面，建议通过请求头传递调用链与操作人信息，便于审计与排障：

- `X-Request-Id`：请求唯一标识（网关生成或调用方生成）
- `X-Operator`：操作人标识（例如用户名/工号；仅在管理端调用场景）
- `X-Tenant-Id`：多租户场景租户标识（如需要）

说明：
- 这些请求头是“建议约定”，不应替代正式鉴权系统；鉴权通常由网关或统一的 Auth 服务处理。
- 如果你希望把操作人字段写入发布日志/审核记录，建议在后续实现中将 `X-Operator` 与服务侧日志/审计字段打通。

## 幂等性建议（面向前端重复点击/重试）

列表查询天然幂等；以下写接口建议考虑幂等（避免重复点击造成重复写入/重复任务）：

- 提交审核：`POST /drafts/{draftId}/submit-review`
- 审核决策：`POST /drafts/{draftId}/review`
- 发起发布：`POST /drafts/{draftId}/publish`
- 回滚发布：`POST /drafts/{draftId}/rollback`

建议策略（不强依赖某一种实现）：
- 前端禁用按钮 + 乐观提示（第一层防重复）
- 服务端通过“状态机约束 + 唯一约束”保障安全（例如任务幂等键、快照唯一键）
- 发布接口已支持 `Idempotency-Key` 请求头；也可通过请求体 `idempotencyKey` 字段传入
- 相同草稿 + 相同幂等键的重复发布请求会复用同一条发布命令，不会重复生成快照和任务



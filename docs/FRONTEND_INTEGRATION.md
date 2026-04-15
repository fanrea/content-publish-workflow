# Frontend Integration（前端对接指南）

本文件面向前端/联调同学，聚焦“怎么调用、怎么分页、怎么处理错误”。

## 基本约定

- 所有业务接口以 `/api/workflows` 为前缀。
- 服务只输出 JSON，不渲染页面。
- 统一返回包裹：`ApiResponse<T>`（见 `docs/API_DESIGN.md`）。

## 时间格式

接口里的时间字段与时间查询参数，建议统一使用 ISO-8601：

- `2026-04-14T12:00:00`

## 列表页推荐接口

列表页不要直接调用 `GET /drafts`（它包含 `body`，属于大字段）。

推荐使用：

- `GET /drafts/page`：分页摘要（不含 body）
- `GET /drafts/stats`：状态计数（Tab/Badge）

### 分页参数

`/drafts/page` 使用：
- `pageNo`：从 1 开始
- `pageSize`：建议 <= 100

返回：
- `items`：当前页列表
- `total`：总条数
- `totalPages`：总页数

## 错误处理

后端会同时使用：
- HTTP 状态码（用于区分 400/404/409/500 等）
- `ApiResponse.success=false` + `code/message`（用于前端展示/埋点）

建议前端处理逻辑：
- 先根据 HTTP 状态码决定是否走“业务提示”还是“系统错误兜底”
- 再根据 `code` 做细分（例如 `INVALID_WORKFLOW_STATE` 提示用户刷新状态）

完整错误码见 `docs/ERROR_CODES.md`。



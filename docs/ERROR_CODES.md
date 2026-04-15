# Error Codes（错误码约定）

本项目采用“HTTP 状态码 + 业务错误码”的组合：
- HTTP 状态码用于粗分类（400/404/409/500）
- `ApiResponse.code` 用于细分业务错误原因

## 通用

- `OK`：成功
- `VALIDATION_ERROR`（HTTP 400）：参数校验失败（Bean Validation）
- `INTERNAL_ERROR`（HTTP 500）：未预期异常（当前返回了异常 message；生产建议收敛为通用提示）

## 工作流相关

- `DRAFT_NOT_FOUND`（HTTP 404）：草稿不存在
- `SNAPSHOT_NOT_FOUND`（HTTP 404）：目标快照/发布版本不存在
- `INVALID_WORKFLOW_STATE`（HTTP 409）：当前工作流状态不允许该操作



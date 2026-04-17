# 错误码说明

项目采用“HTTP 状态码 + 业务错误码”双层表达方式。

## 一、设计原则

- HTTP 状态码负责区分错误大类
- `ApiResponse.code` 负责区分业务语义
- 错误码尽量稳定，避免前端和调用方因为文案变化而失效

## 二、常见 HTTP 状态码

- `200`：读取成功或业务动作成功
- `201`：创建成功
- `400`：请求参数不合法
- `401`：缺少必要认证或角色请求头
- `403`：角色不合法或权限不足
- `404`：目标对象不存在
- `409`：当前状态不允许执行该动作
- `500`：未处理的系统异常

## 三、通用错误码

### `OK`

表示调用成功。

### `VALIDATION_ERROR`

说明：

- 参数校验失败
- 一般对应 `400`

常见原因：

- DTO 字段为空
- 数值超出范围
- 枚举值不合法

### `INTERNAL_ERROR`

说明：

- 未被业务层显式转换的异常
- 一般对应 `500`

## 四、工作流相关错误码

### `DRAFT_NOT_FOUND`

说明：

- 草稿不存在

### `SNAPSHOT_NOT_FOUND`

说明：

- 指定快照或目标发布版本不存在

### `INVALID_WORKFLOW_STATE`

说明：

- 当前草稿状态不允许执行目标动作

常见场景：

- 未审核通过就发起发布
- 非审核中状态执行审核
- 非已发布状态执行下线

## 五、任务恢复相关错误码

### `PUBLISH_TASK_NOT_FOUND`

说明：

- 指定发布任务不存在

### `INVALID_TASK_STATUS`

说明：

- 只有 `FAILED` 或 `DEAD` 的任务允许人工恢复

### `STALE_PUBLISH_TASK`

说明：

- 只允许恢复当前 `publishedVersion` 对应的任务
- 历史版本任务会被拒绝恢复

### `NO_RECOVERABLE_PUBLISH_TASK`

说明：

- 当前发布版本下没有可以恢复的失败任务

## 六、Outbox 恢复相关错误码

### `OUTBOX_EVENT_NOT_FOUND`

说明：

- 指定 outbox 事件不存在

### `INVALID_OUTBOX_STATUS`

说明：

- 只有 `FAILED` 或 `DEAD` 的 outbox 事件允许人工恢复

## 七、权限相关错误码

### `UNAUTHORIZED`

说明：

- 缺少必要请求头
- 缺少角色信息
- 缺少操作人信息

### `FORBIDDEN`

说明：

- 角色值不合法
- 当前角色没有访问目标接口的权限

## 八、使用建议

- 前端提示时，不要只看 `message`，要同时看 `code`
- 状态冲突优先根据 `INVALID_WORKFLOW_STATE` 做交互兜底
- 人工恢复页面要对 `INVALID_TASK_STATUS`、`STALE_PUBLISH_TASK`、`INVALID_OUTBOX_STATUS` 做明确提示

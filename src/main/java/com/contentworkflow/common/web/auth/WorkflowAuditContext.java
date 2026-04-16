package com.contentworkflow.common.web.auth;

/**
 * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
 */
public record WorkflowAuditContext(
        String traceId,
        String requestId
) {
}

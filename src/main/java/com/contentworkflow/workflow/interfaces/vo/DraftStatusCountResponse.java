package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */
public record DraftStatusCountResponse(
        WorkflowStatus status,
        long count
) {
}


package com.contentworkflow.workflow.interfaces.vo;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */
public record ManualRecoveryResponse(
        String targetType,
        Long targetId,
        Long draftId,
        String beforeStatus,
        String afterStatus,
        String operator,
        String remark,
        LocalDateTime operatedAt
) {
}

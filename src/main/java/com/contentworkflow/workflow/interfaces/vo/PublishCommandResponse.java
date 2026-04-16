package com.contentworkflow.workflow.interfaces.vo;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */

public record PublishCommandResponse(
        Long id,
        String commandType,
        String idempotencyKey,
        String operatorName,
        String remark,
        String status,
        Integer targetPublishedVersion,
        Long snapshotId,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

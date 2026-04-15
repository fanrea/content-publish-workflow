package com.contentworkflow.workflow.interfaces.vo;

import java.time.LocalDateTime;

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

package com.contentworkflow.workflow.interfaces.vo;

import java.time.LocalDateTime;

/**
 * Response for manual retry / requeue operations.
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

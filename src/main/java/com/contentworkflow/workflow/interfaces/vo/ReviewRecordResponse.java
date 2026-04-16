package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */

public record ReviewRecordResponse(
        Long id,
        Integer draftVersion,
        String reviewer,
        ReviewDecision decision,
        String comment,
        LocalDateTime reviewedAt
) {
}

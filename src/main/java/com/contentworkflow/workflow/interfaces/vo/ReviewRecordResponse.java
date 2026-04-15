package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;

import java.time.LocalDateTime;

public record ReviewRecordResponse(
        Long id,
        Integer draftVersion,
        String reviewer,
        ReviewDecision decision,
        String comment,
        LocalDateTime reviewedAt
) {
}

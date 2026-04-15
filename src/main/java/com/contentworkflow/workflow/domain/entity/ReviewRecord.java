package com.contentworkflow.workflow.domain.entity;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewRecord {
    private Long id;
    private Long draftId;
    private Integer draftVersion;
    private String reviewer;
    private ReviewDecision decision;
    private String comment;
    private LocalDateTime reviewedAt;
}

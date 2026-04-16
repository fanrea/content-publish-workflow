package com.contentworkflow.workflow.domain.entity;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */

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

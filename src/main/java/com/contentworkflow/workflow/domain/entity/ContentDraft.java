package com.contentworkflow.workflow.domain.entity;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */

@Data
@Builder
public class ContentDraft {
    private Long id;
    private String bizNo;
    private String title;
    private String summary;
    private String body;
    private Integer draftVersion;
    private Integer publishedVersion;
    private WorkflowStatus status;
    private Long currentSnapshotId;
    private String lastReviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

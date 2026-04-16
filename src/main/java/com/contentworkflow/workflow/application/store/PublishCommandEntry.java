package com.contentworkflow.workflow.application.store;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
@Data
@Builder
public class PublishCommandEntry {
    private Long id;
    private Long draftId;
    private String commandType;
    private String idempotencyKey;

    private String operatorName;
    private String remark;

    private String status;
    private Integer targetPublishedVersion;
    private Long snapshotId;
    private String errorMessage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

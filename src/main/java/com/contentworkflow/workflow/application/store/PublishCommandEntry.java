package com.contentworkflow.workflow.application.store;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Idempotent publish command record.
 *
 * <p>Repeated publish requests with the same idempotency key should reuse the same command
 * instead of creating duplicate snapshots and publish tasks.</p>
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

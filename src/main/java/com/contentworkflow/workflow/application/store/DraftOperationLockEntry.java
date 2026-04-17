package com.contentworkflow.workflow.application.store;

import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Lease-based lock for serializing draft operations across instances.
 */
@Data
@Builder
public class DraftOperationLockEntry {
    private Long draftId;
    private DraftOperationType operationType;
    private Integer targetPublishedVersion;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime expiresAt;
}

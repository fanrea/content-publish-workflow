package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DraftOperationLockJpaEntity {

    private Long draftId;
    private DraftOperationType operationType;
    private Integer targetPublishedVersion;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime expiresAt;
}

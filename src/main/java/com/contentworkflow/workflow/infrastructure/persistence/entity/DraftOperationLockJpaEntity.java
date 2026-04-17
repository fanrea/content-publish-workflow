package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persistent lease row used to serialize long-running draft operations.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "draft_operation_lock")
public class DraftOperationLockJpaEntity {

    @Id
    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private DraftOperationType operationType;

    @Column(name = "target_published_version")
    private Integer targetPublishedVersion;

    @Column(name = "locked_by", nullable = false, length = 128)
    private String lockedBy;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}

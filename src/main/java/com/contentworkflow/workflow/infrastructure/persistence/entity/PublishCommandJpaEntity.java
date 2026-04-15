package com.contentworkflow.workflow.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "content_publish_command",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_publish_command", columnNames = {"draft_id", "command_type", "idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_publish_command_draft_time", columnList = "draft_id, created_at"),
                @Index(name = "idx_publish_command_status", columnList = "command_status, updated_at")
        }
)
public class PublishCommandJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "command_type", nullable = false, length = 32)
    private String commandType;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "command_status", nullable = false, length = 32)
    private String commandStatus;

    @Column(name = "target_published_version", nullable = false)
    private Integer targetPublishedVersion;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        // 始终刷新更新时间，便于查询“最新命令状态”以及排查问题。
        updatedAt = LocalDateTime.now();
    }
}

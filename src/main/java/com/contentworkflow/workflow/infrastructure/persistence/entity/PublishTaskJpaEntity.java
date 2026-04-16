package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 持久化实体，用于映射数据库记录并承载 ORM 层的字段信息。
 */

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "content_publish_task",
        indexes = {
                @Index(name = "idx_task_draft_id", columnList = "draft_id"),
                @Index(name = "idx_task_status", columnList = "task_status"),
                @Index(name = "idx_task_status_next_run", columnList = "task_status,next_run_at"),
                @Index(name = "idx_task_updated_at", columnList = "updated_at")
        }
)
public class PublishTaskJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "published_version", nullable = false)
    private Integer publishedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 64)
    private PublishTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false, length = 32)
    private PublishTaskStatus status;

    @Column(name = "retry_times", nullable = false)
    private Integer retryTimes;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /**
     * Next runnable time (for backoff / delayed retries).
     */
    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    /**
     * Worker id that currently holds the task lease.
     */
    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    /**
     * Time when the task was leased/locked.
     */
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 处理 pre persist 相关逻辑，并返回对应的执行结果。
     */

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (retryTimes == null) {
            retryTimes = 0;
        }
        if (status == null) {
            status = PublishTaskStatus.PENDING;
        }
    }

    /**
     * 处理 pre update 相关逻辑，并返回对应的执行结果。
     */

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

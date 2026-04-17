package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
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
        name = "content_draft",
        indexes = {
                @Index(name = "idx_content_draft_biz_no", columnList = "biz_no", unique = true),
                @Index(name = "idx_content_draft_status", columnList = "workflow_status")
        }
)
public class ContentDraftJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long version;

    @Column(name = "biz_no", nullable = false, length = 64, unique = true)
    private String bizNo;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Lob
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "draft_version", nullable = false)
    private Integer draftVersion;

    @Column(name = "published_version", nullable = false)
    private Integer publishedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 32)
    private WorkflowStatus workflowStatus;

    @Column(name = "current_snapshot_id")
    private Long currentSnapshotId;

    @Column(name = "last_review_comment", length = 500)
    private String lastReviewComment;

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
        if (draftVersion == null) {
            draftVersion = 1;
        }
        if (publishedVersion == null) {
            publishedVersion = 0;
        }
        if (version == null) {
            version = 0L;
        }
        if (workflowStatus == null) {
            workflowStatus = WorkflowStatus.DRAFT;
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


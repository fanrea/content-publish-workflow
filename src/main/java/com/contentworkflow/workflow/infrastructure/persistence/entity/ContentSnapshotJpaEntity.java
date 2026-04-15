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
        name = "content_publish_snapshot",
        indexes = {
                @Index(name = "idx_snapshot_draft_id", columnList = "draft_id"),
                @Index(name = "idx_snapshot_published_version", columnList = "published_version")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_snapshot_draft_version", columnNames = {"draft_id", "published_version"})
        }
)
public class ContentSnapshotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "published_version", nullable = false)
    private Integer publishedVersion;

    @Column(name = "source_draft_version", nullable = false)
    private Integer sourceDraftVersion;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Lob
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "rollback_flag", nullable = false)
    private boolean rollback;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @PrePersist
    public void prePersist() {
        if (publishedAt == null) {
            publishedAt = LocalDateTime.now();
        }
    }
}


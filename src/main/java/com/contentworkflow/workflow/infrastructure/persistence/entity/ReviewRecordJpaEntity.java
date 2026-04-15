package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;
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
        name = "content_review_record",
        indexes = {
                @Index(name = "idx_review_record_draft_id", columnList = "draft_id"),
                @Index(name = "idx_review_record_reviewed_at", columnList = "reviewed_at")
        }
)
public class ReviewRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "draft_version", nullable = false)
    private Integer draftVersion;

    @Column(name = "reviewer", nullable = false, length = 64)
    private String reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private ReviewDecision decision;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @PrePersist
    public void prePersist() {
        if (reviewedAt == null) {
            reviewedAt = LocalDateTime.now();
        }
    }
}


package com.contentworkflow.workflow.infrastructure.persistence.mapper;

import com.contentworkflow.workflow.domain.entity.ReviewRecord;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordJpaEntity;

public final class ReviewRecordMapper {
    private ReviewRecordMapper() {
    }

    public static ReviewRecord toDomain(ReviewRecordJpaEntity e) {
        if (e == null) {
            return null;
        }
        return ReviewRecord.builder()
                .id(e.getId())
                .draftId(e.getDraftId())
                .draftVersion(e.getDraftVersion())
                .reviewer(e.getReviewer())
                .decision(e.getDecision())
                .comment(e.getComment())
                .reviewedAt(e.getReviewedAt())
                .build();
    }

    public static ReviewRecordJpaEntity toEntity(ReviewRecord d) {
        if (d == null) {
            return null;
        }
        ReviewRecordJpaEntity e = new ReviewRecordJpaEntity();
        e.setId(d.getId());
        e.setDraftId(d.getDraftId());
        e.setDraftVersion(d.getDraftVersion());
        e.setReviewer(d.getReviewer());
        e.setDecision(d.getDecision());
        e.setComment(d.getComment());
        e.setReviewedAt(d.getReviewedAt());
        return e;
    }
}


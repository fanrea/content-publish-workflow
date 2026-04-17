package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ReviewRecordJpaEntity {

    private Long id;
    private Long draftId;
    private Integer draftVersion;
    private String reviewer;
    private ReviewDecision decision;
    private String comment;
    private LocalDateTime reviewedAt;

    public void prepareForInsert() {
        if (reviewedAt == null) {
            reviewedAt = LocalDateTime.now();
        }
    }
}

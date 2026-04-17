package com.contentworkflow.workflow.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ContentSnapshotJpaEntity {

    private Long id;
    private Long draftId;
    private Integer publishedVersion;
    private Integer sourceDraftVersion;
    private String title;
    private String summary;
    private String body;
    private String operatorName;
    private boolean rollback;
    private LocalDateTime publishedAt;

    public void prepareForInsert() {
        if (publishedAt == null) {
            publishedAt = LocalDateTime.now();
        }
    }
}

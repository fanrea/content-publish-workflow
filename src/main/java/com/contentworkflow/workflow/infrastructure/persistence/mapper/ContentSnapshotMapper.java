package com.contentworkflow.workflow.infrastructure.persistence.mapper;

import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotJpaEntity;

public final class ContentSnapshotMapper {
    private ContentSnapshotMapper() {
    }

    public static ContentSnapshot toDomain(ContentSnapshotJpaEntity e) {
        if (e == null) {
            return null;
        }
        return ContentSnapshot.builder()
                .id(e.getId())
                .draftId(e.getDraftId())
                .publishedVersion(e.getPublishedVersion())
                .sourceDraftVersion(e.getSourceDraftVersion())
                .title(e.getTitle())
                .summary(e.getSummary())
                .body(e.getBody())
                .operator(e.getOperatorName())
                .rollback(e.isRollback())
                .publishedAt(e.getPublishedAt())
                .build();
    }

    public static ContentSnapshotJpaEntity toEntity(ContentSnapshot d) {
        if (d == null) {
            return null;
        }
        ContentSnapshotJpaEntity e = new ContentSnapshotJpaEntity();
        e.setId(d.getId());
        e.setDraftId(d.getDraftId());
        e.setPublishedVersion(d.getPublishedVersion());
        e.setSourceDraftVersion(d.getSourceDraftVersion());
        e.setTitle(d.getTitle());
        e.setSummary(d.getSummary());
        e.setBody(d.getBody());
        e.setOperatorName(d.getOperator());
        e.setRollback(d.isRollback());
        e.setPublishedAt(d.getPublishedAt());
        return e;
    }
}


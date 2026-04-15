package com.contentworkflow.workflow.infrastructure.persistence.mapper;

import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftJpaEntity;

public final class ContentDraftMapper {
    private ContentDraftMapper() {
    }

    public static ContentDraft toDomain(ContentDraftJpaEntity e) {
        if (e == null) {
            return null;
        }
        return ContentDraft.builder()
                .id(e.getId())
                .bizNo(e.getBizNo())
                .title(e.getTitle())
                .summary(e.getSummary())
                .body(e.getBody())
                .draftVersion(e.getDraftVersion())
                .publishedVersion(e.getPublishedVersion())
                .status(e.getWorkflowStatus())
                .currentSnapshotId(e.getCurrentSnapshotId())
                .lastReviewComment(e.getLastReviewComment())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public static ContentDraftJpaEntity toEntity(ContentDraft d) {
        if (d == null) {
            return null;
        }
        ContentDraftJpaEntity e = new ContentDraftJpaEntity();
        e.setId(d.getId());
        e.setBizNo(d.getBizNo());
        e.setTitle(d.getTitle());
        e.setSummary(d.getSummary());
        e.setBody(d.getBody());
        e.setDraftVersion(d.getDraftVersion());
        e.setPublishedVersion(d.getPublishedVersion());
        e.setWorkflowStatus(d.getStatus());
        e.setCurrentSnapshotId(d.getCurrentSnapshotId());
        e.setLastReviewComment(d.getLastReviewComment());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}


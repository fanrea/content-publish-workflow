package com.contentworkflow.workflow.infrastructure.persistence.mapper;

import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskEntity;

public final class PublishTaskMapper {

    private PublishTaskMapper() {
    }

    public static PublishTask toDomain(PublishTaskEntity e) {
        if (e == null) {
            return null;
        }
        return PublishTask.builder()
                .id(e.getId())
                .draftId(e.getDraftId())
                .publishedVersion(e.getPublishedVersion())
                .taskType(e.getTaskType())
                .status(e.getStatus())
                .retryTimes(e.getRetryTimes())
                .errorMessage(e.getErrorMessage())
                .nextRunAt(e.getNextRunAt())
                .lockedBy(e.getLockedBy())
                .lockedAt(e.getLockedAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public static PublishTaskEntity toEntity(PublishTask d) {
        if (d == null) {
            return null;
        }
        PublishTaskEntity e = new PublishTaskEntity();
        e.setId(d.getId());
        e.setDraftId(d.getDraftId());
        e.setPublishedVersion(d.getPublishedVersion());
        e.setTaskType(d.getTaskType());
        e.setStatus(d.getStatus());
        e.setRetryTimes(d.getRetryTimes());
        e.setErrorMessage(d.getErrorMessage());
        e.setNextRunAt(d.getNextRunAt());
        e.setLockedBy(d.getLockedBy());
        e.setLockedAt(d.getLockedAt());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}

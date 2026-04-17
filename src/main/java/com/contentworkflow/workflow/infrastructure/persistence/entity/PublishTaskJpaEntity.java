package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PublishTaskJpaEntity {

    private Long id;
    private Long draftId;
    private Integer publishedVersion;
    private PublishTaskType taskType;
    private PublishTaskStatus status;
    private Integer retryTimes;
    private String errorMessage;
    private LocalDateTime nextRunAt;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (retryTimes == null) {
            retryTimes = 0;
        }
        if (status == null) {
            status = PublishTaskStatus.PENDING;
        }
    }
}

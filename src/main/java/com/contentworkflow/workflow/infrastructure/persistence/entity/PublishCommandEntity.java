package com.contentworkflow.workflow.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PublishCommandEntity {

    private Long id;
    private Long draftId;
    private String commandType;
    private String idempotencyKey;
    private String operatorName;
    private String remark;
    private String commandStatus;
    private Integer targetPublishedVersion;
    private Long snapshotId;
    private String errorMessage;
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
    }
}

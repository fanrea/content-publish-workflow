package com.contentworkflow.workflow.domain.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ContentSnapshot {
    private Long id;
    private Long draftId;
    private Integer publishedVersion;
    private Integer sourceDraftVersion;
    private String title;
    private String summary;
    private String body;
    private String operator;
    private boolean rollback;
    private LocalDateTime publishedAt;
}

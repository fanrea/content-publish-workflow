package com.contentworkflow.document.domain.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CollaborativeDocument 类，负责当前模块的业务实现。
 */
@Data
@Builder
public class CollaborativeDocument {
    private Long id;
    private Long version;
    private String docNo;
    private String title;
    private String content;
    private Integer latestRevision;
    private String latestSnapshotRef;
    private Integer latestSnapshotRevision;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.contentworkflow.document.domain.entity;

import com.contentworkflow.document.domain.enums.DocumentChangeType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DocumentRevision 类，负责当前模块的业务实现。
 */
@Data
@Builder
public class DocumentRevision {
    private Long id;
    private Long documentId;
    private Integer revisionNo;
    private Integer baseRevision;
    private String title;
    private String content;
    private String editorId;
    private String editorName;
    private DocumentChangeType changeType;
    private String changeSummary;
    private LocalDateTime createdAt;
}

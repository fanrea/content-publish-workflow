package com.contentworkflow.document.domain.entity;

import com.contentworkflow.document.domain.enums.DocumentCommentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档评论领域对象。
 */
@Data
@Builder
public class DocumentComment {
    private Long id;
    private Long documentId;
    private Integer startOffset;
    private Integer endOffset;
    private String content;
    private DocumentCommentStatus status;
    private String createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private String resolvedById;
    private String resolvedByName;
    private LocalDateTime resolvedAt;
}


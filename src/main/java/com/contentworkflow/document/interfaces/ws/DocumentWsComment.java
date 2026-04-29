package com.contentworkflow.document.interfaces.ws;

import com.contentworkflow.document.domain.enums.DocumentCommentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket 评论载荷。
 */
@Data
public class DocumentWsComment {
    private Long id;
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


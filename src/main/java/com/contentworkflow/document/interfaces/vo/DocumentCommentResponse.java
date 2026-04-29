package com.contentworkflow.document.interfaces.vo;

import com.contentworkflow.document.domain.enums.DocumentCommentStatus;

import java.time.LocalDateTime;

/**
 * 评论响应对象。
 */
public record DocumentCommentResponse(
        Long id,
        Long documentId,
        Integer startOffset,
        Integer endOffset,
        String content,
        DocumentCommentStatus status,
        String createdById,
        String createdByName,
        LocalDateTime createdAt,
        String resolvedById,
        String resolvedByName,
        LocalDateTime resolvedAt
) {
}


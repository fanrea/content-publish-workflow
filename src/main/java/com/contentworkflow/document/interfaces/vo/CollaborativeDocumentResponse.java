package com.contentworkflow.document.interfaces.vo;

import java.time.LocalDateTime;

/**
 * CollaborativeDocumentResponse 响应对象，用于封装接口返回数据。
 */
public record CollaborativeDocumentResponse(
        Long id,
        String docNo,
        String title,
        String content,
        Integer latestRevision,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

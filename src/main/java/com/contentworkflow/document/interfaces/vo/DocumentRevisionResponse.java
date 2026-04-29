package com.contentworkflow.document.interfaces.vo;

import com.contentworkflow.document.domain.enums.DocumentChangeType;

import java.time.LocalDateTime;

/**
 * DocumentRevisionResponse 响应对象，用于封装接口返回数据。
 */
public record DocumentRevisionResponse(
        Long id,
        Long documentId,
        Integer revisionNo,
        Integer baseRevision,
        String title,
        String content,
        String editorId,
        String editorName,
        DocumentChangeType changeType,
        String changeSummary,
        LocalDateTime createdAt
) {
}

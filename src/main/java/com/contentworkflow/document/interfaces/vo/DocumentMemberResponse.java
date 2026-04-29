package com.contentworkflow.document.interfaces.vo;

import com.contentworkflow.document.domain.enums.DocumentMemberRole;

import java.time.LocalDateTime;

/**
 * 文档成员响应对象。
 */
public record DocumentMemberResponse(
        Long id,
        Long documentId,
        String memberId,
        String memberName,
        DocumentMemberRole memberRole,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}


package com.contentworkflow.document.domain.entity;

import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档成员领域对象。
 */
@Data
@Builder
public class DocumentMember {
    private Long id;
    private Long documentId;
    private String memberId;
    private String memberName;
    private DocumentMemberRole memberRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


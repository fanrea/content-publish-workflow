package com.contentworkflow.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.contentworkflow.document.domain.enums.DocumentCommentStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * collaborative_document_comment 表映射。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("collaborative_document_comment")
public class DocumentCommentEntity {

    @TableId(type = IdType.AUTO)
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

    /**
     * 统一补齐默认字段。
     */
    public void prepareForInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = DocumentCommentStatus.OPEN;
        }
    }
}


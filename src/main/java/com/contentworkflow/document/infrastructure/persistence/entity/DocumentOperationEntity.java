package com.contentworkflow.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * collaborative_document_operation 表映射。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("collaborative_document_operation")
public class DocumentOperationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer revisionNo;
    private Integer baseRevision;
    private String sessionId;
    private Long clientSeq;
    private DocumentOpType opType;
    private Integer opPosition;
    private Integer opLength;
    private String opText;
    private String editorId;
    private String editorName;
    private LocalDateTime createdAt;

    /**
     * 统一补齐创建时间，避免空值入库。
     */
    public void prepareForInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

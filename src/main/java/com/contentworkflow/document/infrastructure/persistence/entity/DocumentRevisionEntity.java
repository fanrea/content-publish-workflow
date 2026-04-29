package com.contentworkflow.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.contentworkflow.document.domain.enums.DocumentChangeType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DocumentRevisionEntity 实体类，表示持久化数据结构。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("collaborative_document_revision")
public class DocumentRevisionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer revisionNo;
    private Integer baseRevision;
    private String title;
    private String content;
    private Boolean isSnapshot;
    private String editorId;
    private String editorName;
    private DocumentChangeType changeType;
    private String changeSummary;
    private LocalDateTime createdAt;

    /**
     * 处理 prepareForInsert 相关业务逻辑。
     */
    public void prepareForInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

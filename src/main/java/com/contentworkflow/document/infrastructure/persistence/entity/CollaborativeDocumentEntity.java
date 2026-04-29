package com.contentworkflow.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * CollaborativeDocumentEntity 实体类，表示持久化数据结构。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("collaborative_document")
public class CollaborativeDocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("lock_version")
    private Long version;
    private String docNo;
    private String title;
    private String content;
    private Integer latestRevision;
    private String latestSnapshotRef;
    private Integer latestSnapshotRevision;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 处理 prepareForInsert 相关业务逻辑。
     */
    public void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (version == null) {
            version = 0L;
        }
        if (latestRevision == null || latestRevision <= 0) {
            latestRevision = 1;
        }
    }
}

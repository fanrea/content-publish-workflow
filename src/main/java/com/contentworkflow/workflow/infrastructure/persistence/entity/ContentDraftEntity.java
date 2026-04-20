package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("content_draft")
public class ContentDraftEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("lock_version")
    private Long version;
    private String bizNo;
    private String title;
    private String summary;
    private String body;
    private Integer draftVersion;
    private Integer publishedVersion;
    private WorkflowStatus workflowStatus;
    private Long currentSnapshotId;
    private String lastReviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (draftVersion == null) {
            draftVersion = 1;
        }
        if (publishedVersion == null) {
            publishedVersion = 0;
        }
        if (version == null) {
            version = 0L;
        }
        if (workflowStatus == null) {
            workflowStatus = WorkflowStatus.DRAFT;
        }
    }
}

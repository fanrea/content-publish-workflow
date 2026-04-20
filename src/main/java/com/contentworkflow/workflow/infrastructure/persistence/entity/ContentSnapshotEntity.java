package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("content_publish_snapshot")
public class ContentSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long draftId;
    private Integer publishedVersion;
    private Integer sourceDraftVersion;
    private String title;
    private String summary;
    private String body;
    private String operatorName;
    @TableField("rollback_flag")
    private boolean rollback;
    private LocalDateTime publishedAt;

    public void prepareForInsert() {
        if (publishedAt == null) {
            publishedAt = LocalDateTime.now();
        }
    }
}

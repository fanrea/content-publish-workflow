package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("content_publish_command")
public class PublishCommandEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long draftId;
    private String commandType;
    private String idempotencyKey;
    private String operatorName;
    private String remark;
    private String commandStatus;
    private Integer targetPublishedVersion;
    private Long snapshotId;
    private String errorMessage;
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
    }
}

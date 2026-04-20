package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("draft_operation_lock")
public class DraftOperationLockEntity {

    @TableId(type = IdType.INPUT)
    private Long draftId;
    private DraftOperationType operationType;
    private Integer targetPublishedVersion;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime expiresAt;
}

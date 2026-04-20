package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("content_publish_log")
public class PublishLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long draftId;
    private String traceId;
    private String requestId;
    private String actionType;
    private String operatorId;
    private String operatorName;
    private WorkflowAuditTargetType targetType;
    private Long targetId;
    private Integer publishedVersion;
    private Long taskId;
    private Long outboxEventId;
    private String beforeStatus;
    private String afterStatus;
    @TableField("action_result")
    private WorkflowAuditResult result;
    private String errorCode;
    private String errorMessage;
    private String remark;
    private LocalDateTime createdAt;

    public void prepareForInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (targetType == null) {
            targetType = WorkflowAuditTargetType.CONTENT_DRAFT;
        }
        if (result == null) {
            result = WorkflowAuditResult.SUCCESS;
        }
    }
}

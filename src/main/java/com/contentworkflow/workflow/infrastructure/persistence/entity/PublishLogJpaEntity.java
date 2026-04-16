package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 持久化实体，用于映射数据库记录并承载 ORM 层的字段信息。
 */

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "content_publish_log",
        indexes = {
                @Index(name = "idx_publish_log_draft_id", columnList = "draft_id"),
                @Index(name = "idx_publish_log_created_at", columnList = "created_at"),
                @Index(name = "idx_publish_log_trace_id", columnList = "trace_id")
        }
)
public class PublishLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @Column(name = "operator_id", length = 64)
    private String operatorId;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private WorkflowAuditTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "published_version")
    private Integer publishedVersion;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "outbox_event_id")
    private Long outboxEventId;

    @Column(name = "before_status", length = 32)
    private String beforeStatus;

    @Column(name = "after_status", length = 32)
    private String afterStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_result", nullable = false, length = 32)
    private WorkflowAuditResult result;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 处理 pre persist 相关逻辑，并返回对应的执行结果。
     */

    @PrePersist
    public void prePersist() {
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

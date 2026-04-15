package com.contentworkflow.workflow.domain.entity;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PublishTask {
    private Long id;
    private Long draftId;
    private Integer publishedVersion;
    private PublishTaskType taskType;
    private PublishTaskStatus status;
    private Integer retryTimes;
    private String errorMessage;
    /**
     * 下次可执行时间（用于失败退避、延迟重试）。
     *
     * <p>为空表示立刻可执行。</p>
     */
    private LocalDateTime nextRunAt;
    /**
     * 任务领取者标识（workerId）。
     *
     * <p>用于避免多实例重复执行；并不强制依赖此字段，持久层可用行锁/条件更新实现 claim。</p>
     */
    private String lockedBy;
    /**
     * 任务领取时间（与 lockedBy 配套）。
     *
     * <p>可用作超时回收：当 lockedAt 过旧时，任务可被重新领取。</p>
     */
    private LocalDateTime lockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.contentworkflow.workflow.infrastructure.persistence.mapper;

import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskJpaEntity;

/**
 * 对象映射组件，负责在领域对象、持久化实体和接口模型之间转换数据结构。
 */

public final class PublishTaskMapper {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private PublishTaskMapper() {
    }

    /**
     * 处理 to domain 相关逻辑，并返回对应的执行结果。
     *
     * @param e 参数 e 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static PublishTask toDomain(PublishTaskJpaEntity e) {
        if (e == null) {
            return null;
        }
        return PublishTask.builder()
                .id(e.getId())
                .draftId(e.getDraftId())
                .publishedVersion(e.getPublishedVersion())
                .taskType(e.getTaskType())
                .status(e.getStatus())
                .retryTimes(e.getRetryTimes())
                .errorMessage(e.getErrorMessage())
                .nextRunAt(e.getNextRunAt())
                .lockedBy(e.getLockedBy())
                .lockedAt(e.getLockedAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    /**
     * 处理 to entity 相关逻辑，并返回对应的执行结果。
     *
     * @param d 参数 d 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static PublishTaskJpaEntity toEntity(PublishTask d) {
        if (d == null) {
            return null;
        }
        PublishTaskJpaEntity e = new PublishTaskJpaEntity();
        e.setId(d.getId());
        e.setDraftId(d.getDraftId());
        e.setPublishedVersion(d.getPublishedVersion());
        e.setTaskType(d.getTaskType());
        e.setStatus(d.getStatus());
        e.setRetryTimes(d.getRetryTimes());
        e.setErrorMessage(d.getErrorMessage());
        e.setNextRunAt(d.getNextRunAt());
        e.setLockedBy(d.getLockedBy());
        e.setLockedAt(d.getLockedAt());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}

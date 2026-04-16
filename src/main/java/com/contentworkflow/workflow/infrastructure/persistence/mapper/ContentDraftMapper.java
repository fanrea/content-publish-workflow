package com.contentworkflow.workflow.infrastructure.persistence.mapper;

import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftJpaEntity;

/**
 * 对象映射组件，负责在领域对象、持久化实体和接口模型之间转换数据结构。
 */

public final class ContentDraftMapper {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private ContentDraftMapper() {
    }

    /**
     * 处理 to domain 相关逻辑，并返回对应的执行结果。
     *
     * @param e 参数 e 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static ContentDraft toDomain(ContentDraftJpaEntity e) {
        if (e == null) {
            return null;
        }
        return ContentDraft.builder()
                .id(e.getId())
                .bizNo(e.getBizNo())
                .title(e.getTitle())
                .summary(e.getSummary())
                .body(e.getBody())
                .draftVersion(e.getDraftVersion())
                .publishedVersion(e.getPublishedVersion())
                .status(e.getWorkflowStatus())
                .currentSnapshotId(e.getCurrentSnapshotId())
                .lastReviewComment(e.getLastReviewComment())
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

    public static ContentDraftJpaEntity toEntity(ContentDraft d) {
        if (d == null) {
            return null;
        }
        ContentDraftJpaEntity e = new ContentDraftJpaEntity();
        e.setId(d.getId());
        e.setBizNo(d.getBizNo());
        e.setTitle(d.getTitle());
        e.setSummary(d.getSummary());
        e.setBody(d.getBody());
        e.setDraftVersion(d.getDraftVersion());
        e.setPublishedVersion(d.getPublishedVersion());
        e.setWorkflowStatus(d.getStatus());
        e.setCurrentSnapshotId(d.getCurrentSnapshotId());
        e.setLastReviewComment(d.getLastReviewComment());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}


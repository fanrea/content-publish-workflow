package com.contentworkflow.workflow.infrastructure.persistence.mapper;

import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotJpaEntity;

/**
 * 对象映射组件，负责在领域对象、持久化实体和接口模型之间转换数据结构。
 */

public final class ContentSnapshotMapper {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private ContentSnapshotMapper() {
    }

    /**
     * 处理 to domain 相关逻辑，并返回对应的执行结果。
     *
     * @param e 参数 e 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static ContentSnapshot toDomain(ContentSnapshotJpaEntity e) {
        if (e == null) {
            return null;
        }
        return ContentSnapshot.builder()
                .id(e.getId())
                .draftId(e.getDraftId())
                .publishedVersion(e.getPublishedVersion())
                .sourceDraftVersion(e.getSourceDraftVersion())
                .title(e.getTitle())
                .summary(e.getSummary())
                .body(e.getBody())
                .operator(e.getOperatorName())
                .rollback(e.isRollback())
                .publishedAt(e.getPublishedAt())
                .build();
    }

    /**
     * 处理 to entity 相关逻辑，并返回对应的执行结果。
     *
     * @param d 参数 d 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static ContentSnapshotJpaEntity toEntity(ContentSnapshot d) {
        if (d == null) {
            return null;
        }
        ContentSnapshotJpaEntity e = new ContentSnapshotJpaEntity();
        e.setId(d.getId());
        e.setDraftId(d.getDraftId());
        e.setPublishedVersion(d.getPublishedVersion());
        e.setSourceDraftVersion(d.getSourceDraftVersion());
        e.setTitle(d.getTitle());
        e.setSummary(d.getSummary());
        e.setBody(d.getBody());
        e.setOperatorName(d.getOperator());
        e.setRollback(d.isRollback());
        e.setPublishedAt(d.getPublishedAt());
        return e;
    }
}


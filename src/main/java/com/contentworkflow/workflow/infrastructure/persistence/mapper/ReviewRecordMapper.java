package com.contentworkflow.workflow.infrastructure.persistence.mapper;

import com.contentworkflow.workflow.domain.entity.ReviewRecord;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordJpaEntity;

/**
 * 对象映射组件，负责在领域对象、持久化实体和接口模型之间转换数据结构。
 */

public final class ReviewRecordMapper {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private ReviewRecordMapper() {
    }

    /**
     * 处理 to domain 相关逻辑，并返回对应的执行结果。
     *
     * @param e 参数 e 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static ReviewRecord toDomain(ReviewRecordJpaEntity e) {
        if (e == null) {
            return null;
        }
        return ReviewRecord.builder()
                .id(e.getId())
                .draftId(e.getDraftId())
                .draftVersion(e.getDraftVersion())
                .reviewer(e.getReviewer())
                .decision(e.getDecision())
                .comment(e.getComment())
                .reviewedAt(e.getReviewedAt())
                .build();
    }

    /**
     * 处理 to entity 相关逻辑，并返回对应的执行结果。
     *
     * @param d 参数 d 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static ReviewRecordJpaEntity toEntity(ReviewRecord d) {
        if (d == null) {
            return null;
        }
        ReviewRecordJpaEntity e = new ReviewRecordJpaEntity();
        e.setId(d.getId());
        e.setDraftId(d.getDraftId());
        e.setDraftVersion(d.getDraftVersion());
        e.setReviewer(d.getReviewer());
        e.setDecision(d.getDecision());
        e.setComment(d.getComment());
        e.setReviewedAt(d.getReviewedAt());
        return e;
    }
}


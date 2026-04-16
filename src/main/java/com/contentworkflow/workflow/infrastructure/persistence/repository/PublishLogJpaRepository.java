package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 持久化仓储接口，负责定义数据库访问能力、查询条件以及相关的缓存协同策略。
 */

public interface PublishLogJpaRepository extends JpaRepository<PublishLogJpaEntity, Long> {
    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishLogJpaEntity> findByDraftIdOrderByCreatedAtDesc(Long draftId);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param traceId 链路追踪标识
     * @return 符合条件的结果集合
     */

    List<PublishLogJpaEntity> findByTraceIdOrderByCreatedAtAsc(String traceId);
}

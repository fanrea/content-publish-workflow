package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 持久化仓储接口，负责定义数据库访问能力、查询条件以及相关的缓存协同策略。
 */

public interface ContentSnapshotJpaRepository extends JpaRepository<ContentSnapshotJpaEntity, Long> {
    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    Optional<ContentSnapshotJpaEntity> findByDraftIdAndPublishedVersion(Long draftId, Integer publishedVersion);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    Optional<ContentSnapshotJpaEntity> findTop1ByDraftIdOrderByPublishedVersionDesc(Long draftId);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<ContentSnapshotJpaEntity> findByDraftIdOrderByPublishedVersionDesc(Long draftId);
}


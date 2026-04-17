package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.common.cache.CacheNames;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftJpaEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 持久化仓储接口，负责定义数据库访问能力、查询条件以及相关的缓存协同策略。
 */
public interface ContentDraftJpaRepository extends JpaRepository<ContentDraftJpaEntity, Long>,
        JpaSpecificationExecutor<ContentDraftJpaEntity> {

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param id 主键标识
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    @Override
    @Cacheable(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID,
            key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#p0)",
            unless = "#result == null || !#result.isPresent()")
    Optional<ContentDraftJpaEntity> findById(Long id);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param bizNo 业务编号
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    @Cacheable(cacheNames = CacheNames.DRAFT_DETAIL_BY_BIZ_NO,
            key = "T(com.contentworkflow.common.cache.CacheKeys).draftBizNo(#p0)",
            unless = "#result == null || !#result.isPresent()")
    Optional<ContentDraftJpaEntity> findByBizNo(String bizNo);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @return 符合条件的结果集合
     */

    @Cacheable(cacheNames = CacheNames.DRAFT_LIST_LATEST,
            key = "T(com.contentworkflow.common.cache.CacheKeys).all()",
            unless = "#result == null || #result.isEmpty()")
    List<ContentDraftJpaEntity> findAllByOrderByUpdatedAtDesc();

    /**
     * 统计满足条件的数据数量，用于计数或监控场景。
     *
     * @param workflowStatus 参数 workflowStatus 对应的业务输入值
     * @return 统计值或数量结果
     */

    @Cacheable(cacheNames = CacheNames.DRAFT_STATUS_COUNT,
            key = "T(com.contentworkflow.common.cache.CacheKeys).draftStatusCount(#p0.name())")
    long countByWorkflowStatus(WorkflowStatus workflowStatus);

    /**
     * 保存当前业务对象状态，并返回持久化后的结果。
     *
     * @param entity 待处理实体对象
     * @return 方法处理后的结果对象
     */
    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID,
                    key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#p0.id)",
                    condition = "#p0 != null && #p0.id != null"),
            @CacheEvict(cacheNames = CacheNames.DRAFT_DETAIL_BY_BIZ_NO,
                    key = "T(com.contentworkflow.common.cache.CacheKeys).draftBizNo(#p0.bizNo)",
                    condition = "#p0 != null && #p0.bizNo != null && !#p0.bizNo.isBlank()"),
            @CacheEvict(cacheNames = CacheNames.DRAFT_LIST_LATEST, allEntries = true),
            // For status counters, evict all entries (short TTL + small key space).
            @CacheEvict(cacheNames = CacheNames.DRAFT_STATUS_COUNT, allEntries = true)
    })
    <S extends ContentDraftJpaEntity> S save(S entity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID,
                    key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#p0)",
                    condition = "#p0 != null"),
            @CacheEvict(cacheNames = CacheNames.DRAFT_DETAIL_BY_BIZ_NO,
                    key = "T(com.contentworkflow.common.cache.CacheKeys).draftBizNo(#p3)",
                    condition = "#p3 != null && !#p3.isBlank()"),
            @CacheEvict(cacheNames = CacheNames.DRAFT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DRAFT_STATUS_COUNT, allEntries = true)
    })
    @Query("""
            update ContentDraftJpaEntity draft
               set draft.title = :title,
                   draft.summary = :summary,
                   draft.body = :body,
                   draft.draftVersion = :draftVersion,
                   draft.publishedVersion = :publishedVersion,
                   draft.workflowStatus = :workflowStatus,
                   draft.currentSnapshotId = :currentSnapshotId,
                   draft.lastReviewComment = :lastReviewComment,
                   draft.updatedAt = :updatedAt,
                   draft.version = draft.version + 1
             where draft.id = :id
               and draft.version = :expectedVersion
               and draft.bizNo = :bizNo
               and draft.workflowStatus in :expectedStatuses
            """)
    int conditionalUpdate(@Param("id") Long id,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("expectedStatuses") Collection<WorkflowStatus> expectedStatuses,
                          @Param("bizNo") String bizNo,
                          @Param("title") String title,
                          @Param("summary") String summary,
                          @Param("body") String body,
                          @Param("draftVersion") Integer draftVersion,
                          @Param("publishedVersion") Integer publishedVersion,
                          @Param("workflowStatus") WorkflowStatus workflowStatus,
                          @Param("currentSnapshotId") Long currentSnapshotId,
                          @Param("lastReviewComment") String lastReviewComment,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 删除指定业务对象，并同步清理相关状态。
     *
     * @param id 主键标识
     */

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID,
                    key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#p0)"),
            @CacheEvict(cacheNames = CacheNames.DRAFT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DRAFT_STATUS_COUNT, allEntries = true)
    })
    void deleteById(Long id);
}

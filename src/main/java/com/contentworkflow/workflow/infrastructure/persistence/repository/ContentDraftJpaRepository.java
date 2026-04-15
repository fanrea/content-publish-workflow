package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.common.cache.CacheNames;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftJpaEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Draft persistence repository (JPA).
 *
 * <p>This repository uses Spring Cache annotations to provide transparent caching for draft
 * details and simple counters. Upper layers (store/service) do not need to know the cache backend.</p>
 *
 * <p>Cache is only an acceleration layer, not the source of truth. Invalidation strategy is
 * "evict after write + short TTL".</p>
 */
public interface ContentDraftJpaRepository extends JpaRepository<ContentDraftJpaEntity, Long>,
        JpaSpecificationExecutor<ContentDraftJpaEntity> {

    @Override
    @Cacheable(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID,
            key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#p0)",
            unless = "#result == null || !#result.isPresent()")
    Optional<ContentDraftJpaEntity> findById(Long id);

    @Cacheable(cacheNames = CacheNames.DRAFT_DETAIL_BY_BIZ_NO,
            key = "T(com.contentworkflow.common.cache.CacheKeys).draftBizNo(#p0)",
            unless = "#result == null || !#result.isPresent()")
    Optional<ContentDraftJpaEntity> findByBizNo(String bizNo);

    @Cacheable(cacheNames = CacheNames.DRAFT_LIST_LATEST,
            key = "T(com.contentworkflow.common.cache.CacheKeys).all()",
            unless = "#result == null || #result.isEmpty()")
    List<ContentDraftJpaEntity> findAllByOrderByUpdatedAtDesc();

    @Cacheable(cacheNames = CacheNames.DRAFT_STATUS_COUNT,
            key = "T(com.contentworkflow.common.cache.CacheKeys).draftStatusCount(#p0.name())")
    long countByWorkflowStatus(WorkflowStatus workflowStatus);

    /**
     * Evict related caches after saving to avoid stale reads.
     *
     * <p>We evict instead of using {@code @CachePut} because upper-layer read models do not
     * necessarily expose JPA entities directly.</p>
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

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DRAFT_DETAIL_BY_ID,
                    key = "T(com.contentworkflow.common.cache.CacheKeys).draftId(#p0)"),
            @CacheEvict(cacheNames = CacheNames.DRAFT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DRAFT_STATUS_COUNT, allEntries = true)
    })
    void deleteById(Long id);
}

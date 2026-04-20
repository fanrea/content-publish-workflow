package com.contentworkflow.workflow.application.store;

import com.contentworkflow.common.cache.CacheNames;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftEntity;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ContentDraftMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ContentSnapshotMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.DraftOperationLockMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.DraftStatusCountRow;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishCommandMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishLogMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishTaskMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ReviewRecordMybatisMapper;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(MybatisWorkflowStoreCacheTest.CacheTestConfig.class)
class MybatisWorkflowStoreCacheTest {

    @Autowired
    private MybatisWorkflowStore store;

    @Autowired
    private ContentDraftMybatisMapper draftMapper;

    @Autowired
    private ReviewRecordMybatisMapper reviewMapper;

    @Autowired
    private ContentSnapshotMybatisMapper snapshotMapper;

    @Autowired
    private PublishTaskMybatisMapper taskMapper;

    @Autowired
    private PublishLogMybatisMapper logMapper;

    @Autowired
    private PublishCommandMybatisMapper commandMapper;

    @Autowired
    private DraftOperationLockMybatisMapper operationLockMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(draftMapper, reviewMapper, snapshotMapper, taskMapper, logMapper, commandMapper, operationLockMapper);
        cacheManager.getCacheNames()
                .forEach(cacheName -> {
                    if (cacheManager.getCache(cacheName) != null) {
                        cacheManager.getCache(cacheName).clear();
                    }
                });
    }

    @Test
    void countDraftsByStatus_shouldReuseCacheForEquivalentFiltersAndEvictOnDraftUpdate() {
        when(draftMapper.countByStatus(any(DraftQueryRequest.class)))
                .thenReturn(List.of(countRow(WorkflowStatus.DRAFT, 2L)));

        DraftQueryRequest first = new DraftQueryRequest(
                "  article  ",
                List.of(WorkflowStatus.REVIEWING, WorkflowStatus.DRAFT),
                null,
                1,
                20,
                DraftQueryRequest.DraftSortBy.UPDATED_AT,
                DraftQueryRequest.SortDirection.DESC,
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 30, 23, 59),
                null,
                null
        );
        DraftQueryRequest second = new DraftQueryRequest(
                "article",
                List.of(WorkflowStatus.DRAFT, WorkflowStatus.REVIEWING),
                false,
                9,
                100,
                DraftQueryRequest.DraftSortBy.CREATED_AT,
                DraftQueryRequest.SortDirection.ASC,
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 30, 23, 59),
                null,
                null
        );

        assertThat(store.countDraftsByStatus(first)).containsEntry(WorkflowStatus.DRAFT, 2L);
        assertThat(store.countDraftsByStatus(second)).containsEntry(WorkflowStatus.DRAFT, 2L);
        verify(draftMapper, times(1)).countByStatus(any(DraftQueryRequest.class));

        ContentDraftEntity updatedEntity = draftEntity(7L, 2L, "BIZ-007", "updated");
        when(draftMapper.conditionalUpdate(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1);
        when(draftMapper.selectById(7L)).thenReturn(updatedEntity);

        store.updateDraft(domainDraft(7L, 1L, "BIZ-007", "before"), EnumSet.of(WorkflowStatus.DRAFT));
        assertThat(store.countDraftsByStatus(second)).containsEntry(WorkflowStatus.DRAFT, 2L);
        verify(draftMapper, times(2)).countByStatus(any(DraftQueryRequest.class));
    }

    @Test
    void findDraftById_shouldCacheHitsAndReloadAfterEviction() {
        ContentDraftEntity initial = draftEntity(11L, 1L, "BIZ-011", "before");
        ContentDraftEntity updated = draftEntity(11L, 2L, "BIZ-011", "after");
        when(draftMapper.selectById(11L)).thenReturn(initial, updated, updated);
        when(draftMapper.conditionalUpdate(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1);

        ContentDraft first = store.findDraftById(11L).orElseThrow();
        ContentDraft second = store.findDraftById(11L).orElseThrow();
        assertThat(second.getTitle()).isEqualTo("before");
        verify(draftMapper, times(1)).selectById(11L);

        first.setTitle("after");
        first.setUpdatedAt(LocalDateTime.of(2026, 4, 18, 10, 0));
        ContentDraft persisted = store.updateDraft(first, EnumSet.of(WorkflowStatus.DRAFT));
        assertThat(persisted.getTitle()).isEqualTo("after");

        ContentDraft reloaded = store.findDraftById(11L).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("after");
        verify(draftMapper, times(3)).selectById(11L);
    }

    private static DraftStatusCountRow countRow(WorkflowStatus status, long count) {
        DraftStatusCountRow row = new DraftStatusCountRow();
        row.setStatus(status);
        row.setCnt(count);
        return row;
    }

    private static ContentDraft domainDraft(Long id, Long version, String bizNo, String title) {
        return ContentDraft.builder()
                .id(id)
                .version(version)
                .bizNo(bizNo)
                .title(title)
                .summary("summary")
                .body("body")
                .draftVersion(1)
                .publishedVersion(0)
                .status(WorkflowStatus.DRAFT)
                .updatedAt(LocalDateTime.of(2026, 4, 18, 9, 0))
                .build();
    }

    private static ContentDraftEntity draftEntity(Long id, Long version, String bizNo, String title) {
        ContentDraftEntity entity = new ContentDraftEntity();
        entity.setId(id);
        entity.setVersion(version);
        entity.setBizNo(bizNo);
        entity.setTitle(title);
        entity.setSummary("summary");
        entity.setBody("body");
        entity.setDraftVersion(1);
        entity.setPublishedVersion(0);
        entity.setWorkflowStatus(WorkflowStatus.DRAFT);
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 18, 8, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 4, 18, 10, 0));
        return entity;
    }

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheNames.DRAFT_DETAIL_BY_ID,
                    CacheNames.DRAFT_LIST_LATEST,
                    CacheNames.DRAFT_STATUS_COUNT
            );
        }

        @Bean
        ContentDraftMybatisMapper draftMapper() {
            return mock(ContentDraftMybatisMapper.class);
        }

        @Bean
        ReviewRecordMybatisMapper reviewMapper() {
            return mock(ReviewRecordMybatisMapper.class);
        }

        @Bean
        ContentSnapshotMybatisMapper snapshotMapper() {
            return mock(ContentSnapshotMybatisMapper.class);
        }

        @Bean
        PublishTaskMybatisMapper taskMapper() {
            return mock(PublishTaskMybatisMapper.class);
        }

        @Bean
        PublishLogMybatisMapper logMapper() {
            return mock(PublishLogMybatisMapper.class);
        }

        @Bean
        PublishCommandMybatisMapper commandMapper() {
            return mock(PublishCommandMybatisMapper.class);
        }

        @Bean
        DraftOperationLockMybatisMapper operationLockMapper() {
            return mock(DraftOperationLockMybatisMapper.class);
        }

        @Bean
        MybatisWorkflowStore mybatisWorkflowStore(ContentDraftMybatisMapper draftMapper,
                                                  ReviewRecordMybatisMapper reviewMapper,
                                                  ContentSnapshotMybatisMapper snapshotMapper,
                                                  PublishTaskMybatisMapper taskMapper,
                                                  PublishLogMybatisMapper logMapper,
                                                  PublishCommandMybatisMapper commandMapper,
                                                  DraftOperationLockMybatisMapper operationLockMapper) {
            return new MybatisWorkflowStore(
                    draftMapper,
                    reviewMapper,
                    snapshotMapper,
                    taskMapper,
                    logMapper,
                    commandMapper,
                    operationLockMapper
            );
        }
    }
}

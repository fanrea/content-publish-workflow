package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentSnapshotJpaRepository extends JpaRepository<ContentSnapshotJpaEntity, Long> {
    Optional<ContentSnapshotJpaEntity> findByDraftIdAndPublishedVersion(Long draftId, Integer publishedVersion);

    Optional<ContentSnapshotJpaEntity> findTop1ByDraftIdOrderByPublishedVersionDesc(Long draftId);

    List<ContentSnapshotJpaEntity> findByDraftIdOrderByPublishedVersionDesc(Long draftId);
}


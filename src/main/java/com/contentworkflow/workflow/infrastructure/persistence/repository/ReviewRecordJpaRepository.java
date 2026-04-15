package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRecordJpaRepository extends JpaRepository<ReviewRecordJpaEntity, Long> {
    List<ReviewRecordJpaEntity> findByDraftIdOrderByReviewedAtDesc(Long draftId);

    Optional<ReviewRecordJpaEntity> findTop1ByDraftIdOrderByReviewedAtDesc(Long draftId);
}


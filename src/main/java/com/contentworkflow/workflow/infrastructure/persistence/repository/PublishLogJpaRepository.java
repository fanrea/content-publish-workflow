package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublishLogJpaRepository extends JpaRepository<PublishLogJpaEntity, Long> {
    List<PublishLogJpaEntity> findByDraftIdOrderByCreatedAtDesc(Long draftId);

    List<PublishLogJpaEntity> findByTraceIdOrderByCreatedAtAsc(String traceId);
}

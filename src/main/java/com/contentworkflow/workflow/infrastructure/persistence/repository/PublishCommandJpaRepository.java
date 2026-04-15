package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishCommandJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublishCommandJpaRepository extends JpaRepository<PublishCommandJpaEntity, Long> {

    Optional<PublishCommandJpaEntity> findByDraftIdAndCommandTypeAndIdempotencyKey(Long draftId,
                                                                                    String commandType,
                                                                                    String idempotencyKey);

    List<PublishCommandJpaEntity> findByDraftIdOrderByCreatedAtDesc(Long draftId);
}

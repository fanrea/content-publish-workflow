package com.contentworkflow.common.messaging.outbox;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository {

    Optional<OutboxEventEntity> findById(Long id);

    OutboxEventEntity save(OutboxEventEntity entity);

    List<OutboxEventEntity> saveAll(List<OutboxEventEntity> entities);

    List<OutboxEventEntity> findClaimCandidates(Collection<OutboxEventStatus> statuses,
                                                LocalDateTime now,
                                                LocalDateTime lockExpiredBefore,
                                                Pageable pageable);

    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    List<OutboxEventEntity> findByStatusIn(Collection<OutboxEventStatus> statuses, Pageable pageable);

    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdAndStatusIn(String aggregateType,
                                                                         String aggregateId,
                                                                         Collection<OutboxEventStatus> statuses,
                                                                         Pageable pageable);
}

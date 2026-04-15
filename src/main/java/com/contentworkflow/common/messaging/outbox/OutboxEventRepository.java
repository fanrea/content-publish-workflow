package com.contentworkflow.common.messaging.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Claim 可投递事件（用悲观锁避免多实例重复 claim）。
     *
     * <p>说明：</p>
     * <p>1. 不同数据库对 {@code SKIP LOCKED} 支持不一致；这里使用 {@code PESSIMISTIC_WRITE + 分页} 做一个通用骨架。</p>
     * <p>2. 查询条件包含 {@code lockedAt < lockExpiredBefore}，用于实例崩溃后的锁过期回收。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from OutboxEventEntity e
            where e.status in :statuses
              and (e.nextRetryAt is null or e.nextRetryAt <= :now)
              and (e.lockedAt is null or e.lockedAt < :lockExpiredBefore)
            order by e.createdAt asc
            """)
    List<OutboxEventEntity> findClaimCandidates(@Param("statuses") Collection<OutboxEventStatus> statuses,
                                                @Param("now") LocalDateTime now,
                                                @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
                                                Pageable pageable);

    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    List<OutboxEventEntity> findByStatusIn(Collection<OutboxEventStatus> statuses, Pageable pageable);

    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdAndStatusIn(String aggregateType,
                                                                         String aggregateId,
                                                                         Collection<OutboxEventStatus> statuses,
                                                                         Pageable pageable);
}

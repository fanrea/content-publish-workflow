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

/**
 * 持久化仓储接口，负责定义数据库访问能力、查询条件以及相关的缓存协同策略。
 */

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param statuses 参数 statuses 对应的业务输入值
     * @param now 参数 now 对应的业务输入值
     * @param lockExpiredBefore 参数 lockExpiredBefore 对应的业务输入值
     * @param pageable 参数 pageable 对应的业务输入值
     * @return 符合条件的结果集合
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

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param status 状态值
     * @param pageable 参数 pageable 对应的业务输入值
     * @return 符合条件的结果集合
     */

    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param statuses 参数 statuses 对应的业务输入值
     * @param pageable 参数 pageable 对应的业务输入值
     * @return 符合条件的结果集合
     */

    List<OutboxEventEntity> findByStatusIn(Collection<OutboxEventStatus> statuses, Pageable pageable);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param aggregateType 参数 aggregateType 对应的业务输入值
     * @param aggregateId 相关业务对象的唯一标识
     * @param statuses 参数 statuses 对应的业务输入值
     * @param pageable 参数 pageable 对应的业务输入值
     * @return 符合条件的结果集合
     */

    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdAndStatusIn(String aggregateType,
                                                                         String aggregateId,
                                                                         Collection<OutboxEventStatus> statuses,
                                                                         Pageable pageable);
}

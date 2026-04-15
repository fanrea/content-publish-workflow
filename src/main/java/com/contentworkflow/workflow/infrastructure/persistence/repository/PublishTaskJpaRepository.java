package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PublishTaskJpaRepository extends JpaRepository<PublishTaskJpaEntity, Long> {
    List<PublishTaskJpaEntity> findByStatusOrderByUpdatedAtAsc(PublishTaskStatus status);

    List<PublishTaskJpaEntity> findByDraftIdOrderByUpdatedAtDesc(Long draftId);

    /**
     * Claiming: query a batch of runnable tasks with a write lock to avoid concurrent double-claim.
     *
     * <p>This favors readability and correctness. If higher throughput is needed, it can be
     * replaced by an atomic MySQL {@code UPDATE ... LIMIT} claiming approach.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from PublishTaskJpaEntity t
            where (t.status = com.contentworkflow.workflow.domain.enums.PublishTaskStatus.PENDING
                    or t.status = com.contentworkflow.workflow.domain.enums.PublishTaskStatus.FAILED)
              and (t.nextRunAt is null or t.nextRunAt <= :now)
              and (t.lockedAt is null or t.lockedAt < :lockExpiredBefore)
            order by t.updatedAt asc
            """)
    List<PublishTaskJpaEntity> findRunnableForUpdate(@Param("now") LocalDateTime now,
                                                    @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
                                                    Pageable pageable);

    default List<PublishTaskJpaEntity> findRunnableForUpdate(LocalDateTime now, Pageable pageable) {
        // Default lock expiration threshold: now-60s. Tune in store/worker based on deployment needs.
        return findRunnableForUpdate(now, now.minusSeconds(60), pageable);
    }
}

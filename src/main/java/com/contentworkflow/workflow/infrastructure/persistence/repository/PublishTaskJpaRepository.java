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

/**
 * 持久化仓储接口，负责定义数据库访问能力、查询条件以及相关的缓存协同策略。
 */

public interface PublishTaskJpaRepository extends JpaRepository<PublishTaskJpaEntity, Long> {
    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param status 状态值
     * @return 符合条件的结果集合
     */

    List<PublishTaskJpaEntity> findByStatusOrderByUpdatedAtAsc(PublishTaskStatus status);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishTaskJpaEntity> findByDraftIdOrderByUpdatedAtDesc(Long draftId);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param now 参数 now 对应的业务输入值
     * @param lockExpiredBefore 参数 lockExpiredBefore 对应的业务输入值
     * @param pageable 参数 pageable 对应的业务输入值
     * @return 符合条件的结果集合
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

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param now 参数 now 对应的业务输入值
     * @param pageable 参数 pageable 对应的业务输入值
     * @return 符合条件的结果集合
     */

    default List<PublishTaskJpaEntity> findRunnableForUpdate(LocalDateTime now, Pageable pageable) {
        // Default lock expiration threshold: now-60s. Tune in store/worker based on deployment needs.
        return findRunnableForUpdate(now, now.minusSeconds(60), pageable);
    }
}

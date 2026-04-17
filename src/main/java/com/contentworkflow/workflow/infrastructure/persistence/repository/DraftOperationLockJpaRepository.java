package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import com.contentworkflow.workflow.infrastructure.persistence.entity.DraftOperationLockJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Repository for lease-based draft operation locks.
 */
public interface DraftOperationLockJpaRepository extends JpaRepository<DraftOperationLockJpaEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    insert into draft_operation_lock
                        (draft_id, operation_type, target_published_version, locked_by, locked_at, expires_at)
                    values
                        (:draftId, :operationType, :targetPublishedVersion, :lockedBy, :lockedAt, :expiresAt)
                    """,
            nativeQuery = true
    )
    int insertLock(@Param("draftId") Long draftId,
                   @Param("operationType") String operationType,
                   @Param("targetPublishedVersion") Integer targetPublishedVersion,
                   @Param("lockedBy") String lockedBy,
                   @Param("lockedAt") LocalDateTime lockedAt,
                   @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DraftOperationLockJpaEntity lock
               set lock.operationType = :operationType,
                   lock.targetPublishedVersion = :targetPublishedVersion,
                   lock.lockedBy = :lockedBy,
                   lock.lockedAt = :lockedAt,
                   lock.expiresAt = :expiresAt
             where lock.draftId = :draftId
               and lock.expiresAt <= :now
            """)
    int replaceExpiredLock(@Param("draftId") Long draftId,
                           @Param("operationType") DraftOperationType operationType,
                           @Param("targetPublishedVersion") Integer targetPublishedVersion,
                           @Param("lockedBy") String lockedBy,
                           @Param("lockedAt") LocalDateTime lockedAt,
                           @Param("expiresAt") LocalDateTime expiresAt,
                           @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DraftOperationLockJpaEntity lock
               set lock.lockedBy = :lockedBy,
                   lock.lockedAt = :lockedAt,
                   lock.expiresAt = :expiresAt
             where lock.draftId = :draftId
               and (
                    (:targetPublishedVersion is null and lock.targetPublishedVersion is null)
                    or lock.targetPublishedVersion = :targetPublishedVersion
               )
            """)
    int renewLock(@Param("draftId") Long draftId,
                  @Param("targetPublishedVersion") Integer targetPublishedVersion,
                  @Param("lockedBy") String lockedBy,
                  @Param("lockedAt") LocalDateTime lockedAt,
                  @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from DraftOperationLockJpaEntity lock
             where lock.draftId = :draftId
               and (
                    (:targetPublishedVersion is null and lock.targetPublishedVersion is null)
                    or lock.targetPublishedVersion = :targetPublishedVersion
               )
            """)
    int deleteMatchingLock(@Param("draftId") Long draftId,
                           @Param("targetPublishedVersion") Integer targetPublishedVersion);
}

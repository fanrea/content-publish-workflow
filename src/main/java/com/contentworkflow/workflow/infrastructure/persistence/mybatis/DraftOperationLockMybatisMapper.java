package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import com.contentworkflow.workflow.infrastructure.persistence.entity.DraftOperationLockJpaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface DraftOperationLockMybatisMapper {

    Optional<DraftOperationLockJpaEntity> selectByDraftId(Long draftId);

    int insertLock(@Param("draftId") Long draftId,
                   @Param("operationType") String operationType,
                   @Param("targetPublishedVersion") Integer targetPublishedVersion,
                   @Param("lockedBy") String lockedBy,
                   @Param("lockedAt") LocalDateTime lockedAt,
                   @Param("expiresAt") LocalDateTime expiresAt);

    int replaceExpiredLock(@Param("draftId") Long draftId,
                           @Param("operationType") DraftOperationType operationType,
                           @Param("targetPublishedVersion") Integer targetPublishedVersion,
                           @Param("lockedBy") String lockedBy,
                           @Param("lockedAt") LocalDateTime lockedAt,
                           @Param("expiresAt") LocalDateTime expiresAt,
                           @Param("now") LocalDateTime now);

    int renewLock(@Param("draftId") Long draftId,
                  @Param("targetPublishedVersion") Integer targetPublishedVersion,
                  @Param("lockedBy") String lockedBy,
                  @Param("lockedAt") LocalDateTime lockedAt,
                  @Param("expiresAt") LocalDateTime expiresAt);

    int deleteMatchingLock(@Param("draftId") Long draftId,
                           @Param("targetPublishedVersion") Integer targetPublishedVersion);
}

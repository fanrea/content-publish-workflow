package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import com.contentworkflow.workflow.infrastructure.persistence.entity.DraftOperationLockEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface DraftOperationLockMybatisMapper extends BaseMapper<DraftOperationLockEntity> {

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

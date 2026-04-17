package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskJpaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PublishTaskMybatisMapper {

    int insert(PublishTaskJpaEntity entity);

    int update(PublishTaskJpaEntity entity);

    Optional<PublishTaskJpaEntity> selectById(Long id);

    List<PublishTaskJpaEntity> selectByStatusOrderByUpdatedAtAsc(PublishTaskStatus status);

    List<PublishTaskJpaEntity> selectByDraftIdOrderByUpdatedAtDesc(Long draftId);

    List<PublishTaskJpaEntity> selectRunnableForUpdate(@Param("now") LocalDateTime now,
                                                       @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
                                                       @Param("limit") int limit);
}

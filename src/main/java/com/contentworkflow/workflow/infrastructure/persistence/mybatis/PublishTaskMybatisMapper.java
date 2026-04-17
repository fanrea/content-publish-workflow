package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PublishTaskMybatisMapper {

    int insert(PublishTaskEntity entity);

    int update(PublishTaskEntity entity);

    Optional<PublishTaskEntity> selectById(Long id);

    List<PublishTaskEntity> selectByStatusOrderByUpdatedAtAsc(PublishTaskStatus status);

    List<PublishTaskEntity> selectByDraftIdOrderByUpdatedAtDesc(Long draftId);

    List<PublishTaskEntity> selectRunnableForUpdate(@Param("now") LocalDateTime now,
                                                       @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
                                                       @Param("limit") int limit);
}

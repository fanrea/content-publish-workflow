package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PublishTaskMybatisMapper extends BaseMapper<PublishTaskEntity> {

    default List<PublishTaskEntity> selectByStatusOrderByUpdatedAtAsc(PublishTaskStatus status) {
        return selectList(new LambdaQueryWrapper<PublishTaskEntity>()
                .eq(PublishTaskEntity::getStatus, status)
                .orderByAsc(PublishTaskEntity::getUpdatedAt, PublishTaskEntity::getId));
    }

    default List<PublishTaskEntity> selectByDraftIdOrderByUpdatedAtDesc(Long draftId) {
        return selectList(new LambdaQueryWrapper<PublishTaskEntity>()
                .eq(PublishTaskEntity::getDraftId, draftId)
                .orderByDesc(PublishTaskEntity::getUpdatedAt, PublishTaskEntity::getId));
    }

    List<PublishTaskEntity> selectRunnableForUpdate(@Param("now") LocalDateTime now,
                                                       @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
                                                       @Param("limit") int limit);
}

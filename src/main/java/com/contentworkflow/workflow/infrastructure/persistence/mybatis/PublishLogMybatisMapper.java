package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishLogEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PublishLogMybatisMapper extends BaseMapper<PublishLogEntity> {

    default List<PublishLogEntity> selectByDraftIdOrderByCreatedAtDesc(Long draftId) {
        return selectList(new LambdaQueryWrapper<PublishLogEntity>()
                .eq(PublishLogEntity::getDraftId, draftId)
                .orderByDesc(PublishLogEntity::getCreatedAt, PublishLogEntity::getId));
    }

    default List<PublishLogEntity> selectByTraceIdOrderByCreatedAtAsc(String traceId) {
        return selectList(new LambdaQueryWrapper<PublishLogEntity>()
                .eq(PublishLogEntity::getTraceId, traceId)
                .orderByAsc(PublishLogEntity::getCreatedAt, PublishLogEntity::getId));
    }
}

package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishCommandEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PublishCommandMybatisMapper extends BaseMapper<PublishCommandEntity> {

    default Optional<PublishCommandEntity> selectByUniqueKey(Long draftId, String commandType, String idempotencyKey) {
        LambdaQueryWrapper<PublishCommandEntity> query = new LambdaQueryWrapper<PublishCommandEntity>()
                .eq(PublishCommandEntity::getDraftId, draftId)
                .eq(PublishCommandEntity::getCommandType, commandType)
                .eq(PublishCommandEntity::getIdempotencyKey, idempotencyKey)
                .last("limit 1");
        return Optional.ofNullable(selectOne(query));
    }

    default List<PublishCommandEntity> selectByDraftIdOrderByCreatedAtDesc(Long draftId) {
        return selectList(new LambdaQueryWrapper<PublishCommandEntity>()
                .eq(PublishCommandEntity::getDraftId, draftId)
                .orderByDesc(PublishCommandEntity::getCreatedAt, PublishCommandEntity::getId));
    }
}

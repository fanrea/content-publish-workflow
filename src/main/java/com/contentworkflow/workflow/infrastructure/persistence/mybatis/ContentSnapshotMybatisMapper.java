package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ContentSnapshotMybatisMapper extends BaseMapper<ContentSnapshotEntity> {

    default Optional<ContentSnapshotEntity> selectByDraftIdAndPublishedVersion(Long draftId, Integer publishedVersion) {
        LambdaQueryWrapper<ContentSnapshotEntity> query = new LambdaQueryWrapper<ContentSnapshotEntity>()
                .eq(ContentSnapshotEntity::getDraftId, draftId)
                .eq(ContentSnapshotEntity::getPublishedVersion, publishedVersion)
                .last("limit 1");
        return Optional.ofNullable(selectOne(query));
    }

    default Optional<ContentSnapshotEntity> selectLatestByDraftId(Long draftId) {
        return selectByDraftIdOrderByPublishedVersionDesc(draftId).stream().findFirst();
    }

    default List<ContentSnapshotEntity> selectByDraftIdOrderByPublishedVersionDesc(Long draftId) {
        return selectList(new LambdaQueryWrapper<ContentSnapshotEntity>()
                .eq(ContentSnapshotEntity::getDraftId, draftId)
                .orderByDesc(ContentSnapshotEntity::getPublishedVersion, ContentSnapshotEntity::getId));
    }
}

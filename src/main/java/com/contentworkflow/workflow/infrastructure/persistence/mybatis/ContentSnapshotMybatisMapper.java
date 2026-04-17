package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ContentSnapshotMybatisMapper {

    int insert(ContentSnapshotEntity entity);

    Optional<ContentSnapshotEntity> selectByDraftIdAndPublishedVersion(@Param("draftId") Long draftId,
                                                                          @Param("publishedVersion") Integer publishedVersion);

    Optional<ContentSnapshotEntity> selectLatestByDraftId(Long draftId);

    List<ContentSnapshotEntity> selectByDraftIdOrderByPublishedVersionDesc(Long draftId);
}

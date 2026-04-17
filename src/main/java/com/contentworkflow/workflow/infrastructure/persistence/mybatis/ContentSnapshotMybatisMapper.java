package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotJpaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ContentSnapshotMybatisMapper {

    int insert(ContentSnapshotJpaEntity entity);

    Optional<ContentSnapshotJpaEntity> selectByDraftIdAndPublishedVersion(@Param("draftId") Long draftId,
                                                                          @Param("publishedVersion") Integer publishedVersion);

    Optional<ContentSnapshotJpaEntity> selectLatestByDraftId(Long draftId);

    List<ContentSnapshotJpaEntity> selectByDraftIdOrderByPublishedVersionDesc(Long draftId);
}

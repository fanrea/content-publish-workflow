package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordJpaEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ReviewRecordMybatisMapper {

    int insert(ReviewRecordJpaEntity entity);

    List<ReviewRecordJpaEntity> selectByDraftIdOrderByReviewedAtDesc(Long draftId);

    Optional<ReviewRecordJpaEntity> selectLatestByDraftId(Long draftId);
}

package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ReviewRecordMybatisMapper {

    int insert(ReviewRecordEntity entity);

    List<ReviewRecordEntity> selectByDraftIdOrderByReviewedAtDesc(Long draftId);

    Optional<ReviewRecordEntity> selectLatestByDraftId(Long draftId);
}

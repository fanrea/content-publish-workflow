package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ReviewRecordMybatisMapper extends BaseMapper<ReviewRecordEntity> {

    default List<ReviewRecordEntity> selectByDraftIdOrderByReviewedAtDesc(Long draftId) {
        return selectList(new LambdaQueryWrapper<ReviewRecordEntity>()
                .eq(ReviewRecordEntity::getDraftId, draftId)
                .orderByDesc(ReviewRecordEntity::getReviewedAt, ReviewRecordEntity::getId));
    }

    default Optional<ReviewRecordEntity> selectLatestByDraftId(Long draftId) {
        return selectByDraftIdOrderByReviewedAtDesc(draftId).stream().findFirst();
    }
}

package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishLogJpaEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PublishLogMybatisMapper {

    int insert(PublishLogJpaEntity entity);

    List<PublishLogJpaEntity> selectByDraftIdOrderByCreatedAtDesc(Long draftId);

    List<PublishLogJpaEntity> selectByTraceIdOrderByCreatedAtAsc(String traceId);
}

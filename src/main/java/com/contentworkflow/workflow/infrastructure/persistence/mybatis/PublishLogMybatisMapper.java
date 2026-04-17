package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishLogEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PublishLogMybatisMapper {

    int insert(PublishLogEntity entity);

    List<PublishLogEntity> selectByDraftIdOrderByCreatedAtDesc(Long draftId);

    List<PublishLogEntity> selectByTraceIdOrderByCreatedAtAsc(String traceId);
}

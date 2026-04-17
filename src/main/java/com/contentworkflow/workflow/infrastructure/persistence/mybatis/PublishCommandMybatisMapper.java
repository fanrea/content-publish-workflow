package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishCommandEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PublishCommandMybatisMapper {

    int insert(PublishCommandEntity entity);

    int update(PublishCommandEntity entity);

    Optional<PublishCommandEntity> selectByUniqueKey(@Param("draftId") Long draftId,
                                                        @Param("commandType") String commandType,
                                                        @Param("idempotencyKey") String idempotencyKey);

    List<PublishCommandEntity> selectByDraftIdOrderByCreatedAtDesc(Long draftId);
}

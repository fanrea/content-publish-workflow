package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishCommandJpaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PublishCommandMybatisMapper {

    int insert(PublishCommandJpaEntity entity);

    int update(PublishCommandJpaEntity entity);

    Optional<PublishCommandJpaEntity> selectByUniqueKey(@Param("draftId") Long draftId,
                                                        @Param("commandType") String commandType,
                                                        @Param("idempotencyKey") String idempotencyKey);

    List<PublishCommandJpaEntity> selectByDraftIdOrderByCreatedAtDesc(Long draftId);
}

package com.contentworkflow.common.messaging.outbox.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.common.messaging.outbox.OutboxEventEntity;
import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface OutboxEventMybatisMapper extends BaseMapper<OutboxEventEntity> {

    List<OutboxEventEntity> selectClaimCandidates(@Param("statuses") Collection<OutboxEventStatus> statuses,
                                                  @Param("now") LocalDateTime now,
                                                  @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
                                                  @Param("limit") int limit);

    List<OutboxEventEntity> selectByStatusOrderByCreatedAtAsc(@Param("status") OutboxEventStatus status,
                                                              @Param("limit") int limit,
                                                              @Param("offset") long offset);

    List<OutboxEventEntity> selectByStatusIn(@Param("statuses") Collection<OutboxEventStatus> statuses,
                                             @Param("limit") int limit,
                                             @Param("offset") long offset,
                                             @Param("orderByClause") String orderByClause);

    List<OutboxEventEntity> selectByAggregateAndStatusIn(@Param("aggregateType") String aggregateType,
                                                         @Param("aggregateId") String aggregateId,
                                                         @Param("statuses") Collection<OutboxEventStatus> statuses,
                                                         @Param("limit") int limit,
                                                         @Param("offset") long offset,
                                                         @Param("orderByClause") String orderByClause);
}

package com.contentworkflow.common.messaging.outbox;

import com.contentworkflow.common.messaging.outbox.mybatis.OutboxEventMybatisMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class MybatisOutboxEventRepository implements OutboxEventRepository {

    private final OutboxEventMybatisMapper mapper;

    public MybatisOutboxEventRepository(OutboxEventMybatisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<OutboxEventEntity> findById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public OutboxEventEntity save(OutboxEventEntity entity) {
        if (entity.getId() == null) {
            entity.prepareForInsert();
            mapper.insert(entity);
        } else {
            entity.touchForUpdate();
            mapper.update(entity);
        }
        return entity;
    }

    @Override
    public List<OutboxEventEntity> saveAll(List<OutboxEventEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream().map(this::save).toList();
    }

    @Override
    public List<OutboxEventEntity> findClaimCandidates(Collection<OutboxEventStatus> statuses,
                                                       LocalDateTime now,
                                                       LocalDateTime lockExpiredBefore,
                                                       Pageable pageable) {
        return mapper.selectClaimCandidates(statuses, now, lockExpiredBefore, pageable.getPageSize());
    }

    @Override
    public List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable) {
        return mapper.selectByStatusOrderByCreatedAtAsc(status, pageable.getPageSize(), pageable.getOffset());
    }

    @Override
    public List<OutboxEventEntity> findByStatusIn(Collection<OutboxEventStatus> statuses, Pageable pageable) {
        return mapper.selectByStatusIn(statuses, pageable.getPageSize(), pageable.getOffset(), buildOrderByClause(pageable));
    }

    @Override
    public List<OutboxEventEntity> findByAggregateTypeAndAggregateIdAndStatusIn(String aggregateType,
                                                                                 String aggregateId,
                                                                                 Collection<OutboxEventStatus> statuses,
                                                                                 Pageable pageable) {
        return mapper.selectByAggregateAndStatusIn(
                aggregateType,
                aggregateId,
                statuses,
                pageable.getPageSize(),
                pageable.getOffset(),
                buildOrderByClause(pageable)
        );
    }

    private String buildOrderByClause(Pageable pageable) {
        List<String> clauses = new ArrayList<>();
        Sort sort = pageable == null ? Sort.unsorted() : pageable.getSort();
        if (sort.isSorted()) {
            for (Sort.Order order : sort) {
                clauses.add(resolveSortColumn(order.getProperty()) + " " + resolveDirection(order.getDirection()));
            }
        }
        if (clauses.isEmpty()) {
            clauses.add("created_at DESC");
        }
        boolean hasIdSort = sort.stream().anyMatch(order -> "id".equals(order.getProperty()));
        if (!hasIdSort) {
            String tieBreakerDirection = sort.isSorted()
                    ? resolveDirection(sort.iterator().next().getDirection())
                    : "DESC";
            clauses.add("id " + tieBreakerDirection);
        }
        return String.join(", ", clauses);
    }

    private String resolveSortColumn(String property) {
        return switch (property) {
            case "id" -> "id";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "nextRetryAt" -> "next_retry_at";
            case "attempt" -> "attempt";
            case "status" -> "status";
            default -> throw new IllegalArgumentException("Unsupported outbox sort property: " + property);
        };
    }

    private String resolveDirection(Sort.Direction direction) {
        return direction == null ? "ASC" : direction.name().toUpperCase(Locale.ROOT);
    }
}

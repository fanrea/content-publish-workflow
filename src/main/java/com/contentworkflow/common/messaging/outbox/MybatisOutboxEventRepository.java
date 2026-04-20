package com.contentworkflow.common.messaging.outbox;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.common.messaging.WorkflowMessagingTraceContext;
import com.contentworkflow.common.messaging.outbox.mybatis.OutboxEventMybatisMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class MybatisOutboxEventRepository implements OutboxEventRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final OutboxEventMybatisMapper mapper;
    private final ObjectMapper objectMapper;

    public MybatisOutboxEventRepository(OutboxEventMybatisMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<OutboxEventEntity> findById(Long id) {
        return Optional.ofNullable(hydrateTraceContext(mapper.selectById(id)));
    }

    @Override
    public OutboxEventEntity save(OutboxEventEntity entity) {
        synchronizeTraceContext(entity);
        if (entity.getId() == null) {
            entity.prepareForInsert();
            mapper.insert(entity);
        } else {
            entity.touchForUpdate();
            mapper.updateById(entity);
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
        return mapper.selectClaimCandidates(statuses, now, lockExpiredBefore, pageable.getPageSize())
                .stream()
                .map(this::hydrateTraceContext)
                .toList();
    }

    @Override
    public List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable) {
        return mapper.selectByStatusOrderByCreatedAtAsc(status, pageable.getPageSize(), pageable.getOffset())
                .stream()
                .map(this::hydrateTraceContext)
                .toList();
    }

    @Override
    public List<OutboxEventEntity> findByStatusIn(Collection<OutboxEventStatus> statuses, Pageable pageable) {
        return mapper.selectByStatusIn(statuses, pageable.getPageSize(), pageable.getOffset(), buildOrderByClause(pageable))
                .stream()
                .map(this::hydrateTraceContext)
                .toList();
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
        ).stream().map(this::hydrateTraceContext).toList();
    }

    private OutboxEventEntity hydrateTraceContext(OutboxEventEntity entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> headers = parseHeaders(entity.getHeadersJson());
        if (isBlank(entity.getTraceId())) {
            entity.setTraceId(resolveTraceId(headers));
        }
        if (isBlank(entity.getRequestId())) {
            entity.setRequestId(resolveRequestId(headers));
        }
        return entity;
    }

    private void synchronizeTraceContext(OutboxEventEntity entity) {
        if (entity == null) {
            return;
        }
        Map<String, Object> headers = new LinkedHashMap<>(parseHeaders(entity.getHeadersJson()));
        putIfPresent(headers, WorkflowLogContext.TRACE_ID_HEADER, entity.getTraceId());
        putIfPresent(headers, WorkflowLogContext.TRACE_ID_KEY, entity.getTraceId());
        putIfPresent(headers, WorkflowLogContext.REQUEST_ID_HEADER, entity.getRequestId());
        putIfPresent(headers, WorkflowLogContext.REQUEST_ID_KEY, entity.getRequestId());

        Map<String, Object> normalizedHeaders = new LinkedHashMap<>(WorkflowMessagingTraceContext.enrichOutboundHeaders(headers));
        String traceId = firstNonBlank(entity.getTraceId(), resolveTraceId(normalizedHeaders));
        String requestId = firstNonBlank(entity.getRequestId(), resolveRequestId(normalizedHeaders));

        entity.setTraceId(traceId);
        entity.setRequestId(requestId);
        entity.setHeadersJson(toJson(normalizedHeaders));
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

    private Map<String, Object> parseHeaders(String headersJson) {
        if (isBlank(headersJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> headers = objectMapper.readValue(headersJson, MAP_TYPE);
            if (headers == null || headers.isEmpty()) {
                return Map.of();
            }
            return headers;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String resolveTraceId(Map<String, Object> headers) {
        return firstNonBlank(
                headerValue(headers, WorkflowLogContext.TRACE_ID_HEADER),
                headerValue(headers, WorkflowLogContext.TRACE_ID_KEY),
                headerValue(headers, WorkflowLogContext.B3_TRACE_ID_HEADER)
        );
    }

    private String resolveRequestId(Map<String, Object> headers) {
        return firstNonBlank(
                headerValue(headers, WorkflowLogContext.REQUEST_ID_HEADER),
                headerValue(headers, WorkflowLogContext.REQUEST_ID_KEY)
        );
    }

    private String headerValue(Map<String, Object> headers, String key) {
        if (headers == null || headers.isEmpty() || key == null) {
            return null;
        }
        Object value = headers.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private void putIfPresent(Map<String, Object> headers, String key, String value) {
        if (!isBlank(value)) {
            headers.put(key, value.trim());
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

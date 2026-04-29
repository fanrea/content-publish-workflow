package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "workflow.realtime.recent-updates", name = "enabled", havingValue = "true")
public class RedisDocumentRealtimeRecentUpdateCache implements DocumentRealtimeRecentUpdateCache {

    private static final Logger log = LoggerFactory.getLogger(RedisDocumentRealtimeRecentUpdateCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int maxSize;
    private final Duration ttl;

    public RedisDocumentRealtimeRecentUpdateCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${workflow.realtime.recent-updates.size:200}") int maxSize,
            @Value("${workflow.realtime.recent-updates.ttl:120s}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.maxSize = Math.max(10, maxSize);
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofSeconds(120) : ttl;
    }

    RedisDocumentRealtimeRecentUpdateCache(StringRedisTemplate redisTemplate,
                                           ObjectMapper objectMapper,
                                           int maxSize,
                                           Duration ttl,
                                           boolean ignoredForTestCtor) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.maxSize = Math.max(10, maxSize);
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofSeconds(120) : ttl;
    }

    @Override
    public void append(DocumentOperation operation) {
        if (operation == null || operation.getDocumentId() == null || operation.getRevisionNo() == null) {
            return;
        }
        String key = key(operation.getDocumentId());
        try {
            String payload = objectMapper.writeValueAsString(RecentUpdate.from(operation));
            redisTemplate.opsForList().rightPush(key, payload);
            redisTemplate.opsForList().trim(key, -maxSize, -1);
            redisTemplate.expire(key, ttl);
        } catch (Exception ex) {
            log.warn("recent update cache append failed, docId={}, revision={}",
                    operation.getDocumentId(),
                    operation.getRevisionNo(),
                    ex);
        }
    }

    @Override
    public ReplayResult replaySince(Long documentId, int fromRevisionExclusive, int limit) {
        if (documentId == null || documentId <= 0 || limit <= 0) {
            return new ReplayResult(List.of(), false);
        }
        try {
            List<String> raw = redisTemplate.opsForList().range(key(documentId), 0, maxSize - 1L);
            if (raw == null || raw.isEmpty()) {
                return new ReplayResult(List.of(), false);
            }
            List<RecentUpdate> updates = new ArrayList<>(raw.size());
            for (String item : raw) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                try {
                    updates.add(objectMapper.readValue(item, RecentUpdate.class));
                } catch (Exception parseEx) {
                    log.warn("recent update cache parse failed, docId={}", documentId, parseEx);
                    return new ReplayResult(List.of(), false);
                }
            }
            List<RecentUpdate> selected = updates.stream()
                    .filter(update -> update.revisionNo() != null && update.revisionNo() > fromRevisionExclusive)
                    .limit(limit)
                    .toList();
            if (selected.isEmpty()) {
                return new ReplayResult(List.of(), false);
            }
            boolean completeFromBase = isContiguousFrom(selected, fromRevisionExclusive + 1);
            return new ReplayResult(
                    selected.stream().map(RecentUpdate::toOperation).toList(),
                    completeFromBase
            );
        } catch (Exception ex) {
            log.warn("recent update cache replay failed, docId={}, fromRevision={}, limit={}",
                    documentId,
                    fromRevisionExclusive,
                    limit,
                    ex);
            return new ReplayResult(List.of(), false);
        }
    }

    private boolean isContiguousFrom(List<RecentUpdate> selected, int expectedFirstRevision) {
        int expected = expectedFirstRevision;
        for (RecentUpdate update : selected) {
            Integer revision = update.revisionNo();
            if (revision == null || revision != expected) {
                return false;
            }
            expected++;
        }
        return true;
    }

    private String key(Long documentId) {
        return "doc:" + documentId + ":recent_updates";
    }

    record RecentUpdate(
            Long id,
            Long documentId,
            Integer revisionNo,
            Integer baseRevision,
            String editorId,
            String editorName,
            DocumentOpType opType,
            Integer opPosition,
            Integer opLength,
            String opText,
            LocalDateTime createdAt
    ) {
        static RecentUpdate from(DocumentOperation operation) {
            return new RecentUpdate(
                    operation.getId(),
                    operation.getDocumentId(),
                    operation.getRevisionNo(),
                    operation.getBaseRevision(),
                    operation.getEditorId(),
                    operation.getEditorName(),
                    operation.getOpType(),
                    operation.getOpPosition(),
                    operation.getOpLength(),
                    operation.getOpText(),
                    operation.getCreatedAt()
            );
        }

        DocumentOperation toOperation() {
            return DocumentOperation.builder()
                    .id(id)
                    .documentId(documentId)
                    .revisionNo(revisionNo)
                    .baseRevision(baseRevision)
                    .editorId(editorId)
                    .editorName(editorName)
                    .opType(opType)
                    .opPosition(opPosition)
                    .opLength(opLength)
                    .opText(opText)
                    .createdAt(createdAt)
                    .build();
        }
    }
}

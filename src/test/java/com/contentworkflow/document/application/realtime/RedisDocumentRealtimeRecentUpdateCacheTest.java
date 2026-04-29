package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDocumentRealtimeRecentUpdateCacheTest {

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private ListOperations<String, String> listOperations;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOperations = (ListOperations<String, String>) mock(ListOperations.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Test
    void replaySince_shouldReturnCompleteWhenRevisionsContiguousFromBase() throws Exception {
        RedisDocumentRealtimeRecentUpdateCache cache = new RedisDocumentRealtimeRecentUpdateCache(
                redisTemplate,
                objectMapper,
                200,
                Duration.ofSeconds(120),
                true
        );
        String key = "doc:100:recent_updates";
        List<String> payloads = List.of(
                toPayload(op(100L, 4, 3)),
                toPayload(op(100L, 5, 4))
        );
        when(listOperations.range(eq(key), eq(0L), eq(199L))).thenReturn(payloads);

        DocumentRealtimeRecentUpdateCache.ReplayResult result = cache.replaySince(100L, 3, 200);

        assertThat(result.completeFromBase()).isTrue();
        assertThat(result.operations()).hasSize(2);
        assertThat(result.operations().get(0).getRevisionNo()).isEqualTo(4);
        assertThat(result.operations().get(1).getRevisionNo()).isEqualTo(5);
    }

    @Test
    void replaySince_shouldReturnIncompleteWhenFirstRevisionHasGap() throws Exception {
        RedisDocumentRealtimeRecentUpdateCache cache = new RedisDocumentRealtimeRecentUpdateCache(
                redisTemplate,
                objectMapper,
                200,
                Duration.ofSeconds(120),
                true
        );
        String key = "doc:100:recent_updates";
        List<String> payloads = List.of(
                toPayload(op(100L, 5, 4)),
                toPayload(op(100L, 6, 5))
        );
        when(listOperations.range(eq(key), eq(0L), eq(199L))).thenReturn(payloads);

        DocumentRealtimeRecentUpdateCache.ReplayResult result = cache.replaySince(100L, 3, 200);

        assertThat(result.completeFromBase()).isFalse();
        assertThat(result.operations()).hasSize(2);
    }

    @Test
    void append_shouldWriteListTrimAndExpire() {
        RedisDocumentRealtimeRecentUpdateCache cache = new RedisDocumentRealtimeRecentUpdateCache(
                redisTemplate,
                objectMapper,
                200,
                Duration.ofSeconds(120),
                true
        );
        DocumentOperation operation = op(100L, 4, 3);

        cache.append(operation);

        verify(listOperations, times(1)).rightPush(eq("doc:100:recent_updates"), org.mockito.ArgumentMatchers.anyString());
        verify(listOperations, times(1)).trim("doc:100:recent_updates", -200L, -1L);
        verify(redisTemplate, times(1)).expire(eq("doc:100:recent_updates"), eq(Duration.ofSeconds(120)));
    }

    private String toPayload(DocumentOperation operation) throws Exception {
        RedisDocumentRealtimeRecentUpdateCache.RecentUpdate update =
                RedisDocumentRealtimeRecentUpdateCache.RecentUpdate.from(operation);
        return objectMapper.writeValueAsString(update);
    }

    private DocumentOperation op(Long docId, int revision, int baseRevision) {
        return DocumentOperation.builder()
                .id((long) revision)
                .documentId(docId)
                .revisionNo(revision)
                .baseRevision(baseRevision)
                .editorId("u-1")
                .editorName("alice")
                .opType(DocumentOpType.INSERT)
                .opPosition(0)
                .opLength(0)
                .opText("x")
                .createdAt(LocalDateTime.now())
                .build();
    }
}

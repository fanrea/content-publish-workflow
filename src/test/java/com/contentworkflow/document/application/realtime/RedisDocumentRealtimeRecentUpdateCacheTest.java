package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDocumentRealtimeRecentUpdateCacheTest {

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private ListOperations<String, String> listOperations;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOperations = (ListOperations<String, String>) mock(ListOperations.class);
        valueOperations = (ValueOperations<String, String>) mock(ValueOperations.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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

    @Test
    void append_shouldMarkDirtyWhenRedisWriteFails_andReplayShouldDegrade() {
        RedisDocumentRealtimeRecentUpdateCache cache = new RedisDocumentRealtimeRecentUpdateCache(
                redisTemplate,
                objectMapper,
                200,
                Duration.ofSeconds(120),
                true
        );
        DocumentOperation operation = op(100L, 7, 6);
        when(listOperations.rightPush(eq("doc:100:recent_updates"), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("redis timeout"));
        when(valueOperations.get("doc:100:recent_updates:dirty")).thenReturn("7");

        cache.append(operation);
        DocumentRealtimeRecentUpdateCache.ReplayResult result = cache.replaySince(100L, 6, 200);

        verify(valueOperations, times(1))
                .set(eq("doc:100:recent_updates:dirty"), eq("7"), eq(Duration.ofSeconds(120)));
        verify(listOperations, never()).range(eq("doc:100:recent_updates"), eq(0L), eq(199L));
        assertThat(result.completeFromBase()).isFalse();
        assertThat(result.operations()).isEmpty();
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

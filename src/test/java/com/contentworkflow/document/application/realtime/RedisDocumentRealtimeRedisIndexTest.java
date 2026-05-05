package com.contentworkflow.document.application.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDocumentRealtimeRedisIndexTest {

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private HashOperations<String, Object, Object> hashOperations;
    private RedisDocumentRealtimeRedisIndex redisIndex;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = (HashOperations<String, Object, Object>) mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        redisIndex = new RedisDocumentRealtimeRedisIndex(redisTemplate, "gw-1");
    }

    @Test
    void upsertAndRemoveSessionClock_shouldOperateOnSessionClockHash() {
        redisIndex.upsertSessionClock(100L, "s-1", 88L);
        redisIndex.removeSessionClock(100L, "s-1");

        verify(hashOperations, times(1)).put(
                eq("doc:100:session_clocks"),
                eq("s-1"),
                eq("88")
        );
        verify(hashOperations, times(1)).delete(
                eq("doc:100:session_clocks"),
                eq("s-1")
        );
    }

    @Test
    void minimumSessionClock_shouldReturnMinimumPositiveClock() {
        when(hashOperations.entries("doc:100:session_clocks")).thenReturn(Map.of(
                "s-1", "30",
                "s-2", "10",
                "s-3", "invalid",
                "s-4", "-1"
        ));

        OptionalLong minimum = redisIndex.minimumSessionClock(100L);

        assertThat(minimum).hasValue(10L);
    }
}


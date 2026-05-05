package com.contentworkflow.document.application.gc;

import com.contentworkflow.document.application.realtime.DocumentRealtimeRedisIndex;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisIndexCompactionWatermarkProviderTest {

    @Test
    void minimumOnlineClock_shouldReturnClockFromRedisIndex() {
        DocumentRealtimeRedisIndex redisIndex = mock(DocumentRealtimeRedisIndex.class);
        when(redisIndex.minimumSessionClock(100L)).thenReturn(OptionalLong.of(12L));
        RedisIndexCompactionWatermarkProvider provider = new RedisIndexCompactionWatermarkProvider(redisIndex);

        OptionalLong result = provider.minimumOnlineClock(100L);

        assertThat(result).isPresent();
        assertThat(result.getAsLong()).isEqualTo(12L);
        verify(redisIndex, times(1)).minimumSessionClock(100L);
    }

    @Test
    void minimumOnlineClock_shouldReturnEmptyWhenInvalidDocumentId() {
        DocumentRealtimeRedisIndex redisIndex = mock(DocumentRealtimeRedisIndex.class);
        RedisIndexCompactionWatermarkProvider provider = new RedisIndexCompactionWatermarkProvider(redisIndex);

        OptionalLong result = provider.minimumOnlineClock(0L);

        assertThat(result).isEmpty();
        verify(redisIndex, times(0)).minimumSessionClock(0L);
    }

    @Test
    void minimumOnlineClock_shouldReturnEmptyWhenRedisIndexThrows() {
        DocumentRealtimeRedisIndex redisIndex = mock(DocumentRealtimeRedisIndex.class);
        when(redisIndex.minimumSessionClock(100L)).thenThrow(new RuntimeException("redis down"));
        RedisIndexCompactionWatermarkProvider provider = new RedisIndexCompactionWatermarkProvider(redisIndex);

        OptionalLong result = provider.minimumOnlineClock(100L);

        assertThat(result).isEmpty();
        verify(redisIndex, times(1)).minimumSessionClock(100L);
    }
}

package com.contentworkflow.document.application.gc;

import com.contentworkflow.document.application.realtime.DocumentRealtimeRedisIndex;
import com.contentworkflow.document.application.realtime.RedisDocumentRealtimeRedisIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

@Component
@ConditionalOnBean(RedisDocumentRealtimeRedisIndex.class)
@ConditionalOnMissingBean(CompactionWatermarkProvider.class)
public class RedisIndexCompactionWatermarkProvider implements CompactionWatermarkProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisIndexCompactionWatermarkProvider.class);

    private final DocumentRealtimeRedisIndex realtimeRedisIndex;

    public RedisIndexCompactionWatermarkProvider(DocumentRealtimeRedisIndex realtimeRedisIndex) {
        this.realtimeRedisIndex = realtimeRedisIndex;
    }

    @Override
    public OptionalLong minimumOnlineClock(Long documentId) {
        if (documentId == null || documentId <= 0L) {
            return OptionalLong.empty();
        }
        try {
            return realtimeRedisIndex.minimumSessionClock(documentId);
        } catch (Exception ex) {
            log.warn("load minimum online clock from redis index failed, docId={}", documentId, ex);
            return OptionalLong.empty();
        }
    }
}

package com.contentworkflow.document.application.cache;

import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(prefix = "workflow.redis-cache", name = "enabled", havingValue = "true")
public class RedisDocumentCacheService implements DocumentCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisDocumentCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisDocumentCacheService(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${workflow.redis-cache.key-prefix:cpw:doc:cache:}") String keyPrefix,
                                     @Value("${workflow.redis-cache.ttl-seconds:1800}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.ttl = Duration.ofSeconds(Math.max(30, ttlSeconds));
    }

    @Override
    public CollaborativeDocumentEntity get(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(key(documentId));
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, CollaborativeDocumentEntity.class);
        } catch (Exception ex) {
            log.warn("document cache read failed, documentId={}", documentId, ex);
            return null;
        }
    }

    @Override
    public void put(CollaborativeDocumentEntity document) {
        if (document == null || document.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    key(document.getId()),
                    objectMapper.writeValueAsString(document),
                    ttl
            );
        } catch (Exception ex) {
            log.warn("document cache write failed, documentId={}", document.getId(), ex);
        }
    }

    @Override
    public void evict(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return;
        }
        try {
            redisTemplate.delete(key(documentId));
        } catch (Exception ex) {
            log.warn("document cache evict failed, documentId={}", documentId, ex);
        }
    }

    private String key(Long documentId) {
        return keyPrefix + documentId;
    }
}

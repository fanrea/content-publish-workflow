package com.contentworkflow.common.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis + Spring Cache 缁熶竴閰嶇疆銆? *
 * <p>璁捐鍘熷垯锛?/p>
 * <ul>
 *   <li>缂撳瓨鍙槸鍔犻€熷眰锛岄粯璁ら噰鐢?best-effort锛孯edis 寮傚父涓嶅奖鍝嶄富娴佺▼銆?/li>
 *   <li>涓嶅悓 cacheName 浣跨敤涓嶅悓 TTL锛岄伩鍏嶄竴鍒€鍒囥€?/li>
 *   <li>缁熶竴 JSON 搴忓垪鍖栵紝鍏煎 LocalDateTime 绛?Java 鏃堕棿绫诲瀷銆?/li>
 * </ul>
 *
 * <p>榛樿涓嶅己渚濊禆 Redis锛涘彧鏈夊惎鐢?`redis` profile 鍚庯紝缂撳瓨绫诲瀷鎵嶄細鍒囨崲涓?Redis銆?/p>
 */
@EnableCaching
@Configuration
@EnableConfigurationProperties(WorkflowCacheProperties.class)
public class RedisCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(WorkflowCacheProperties props) {
        return builder -> {
            RedisCacheConfiguration defaultCfg = baseRedisCacheConfiguration(Duration.ofMinutes(10), props.getKeyPrefix());

            // Apply the default cache configuration as a fallback.
            builder.cacheDefaults(defaultCfg);

            // Draft detail caches use a shorter TTL and do not cache null values.
            builder.withCacheConfiguration(
                    CacheNames.DRAFT_DETAIL_BY_ID,
                    baseRedisCacheConfiguration(props.getDraftDetailTtl(), props.getKeyPrefix()).disableCachingNullValues()
            );
            builder.withCacheConfiguration(
                    CacheNames.DRAFT_DETAIL_BY_BIZ_NO,
                    baseRedisCacheConfiguration(props.getDraftDetailTtl(), props.getKeyPrefix()).disableCachingNullValues()
            );

            // Draft list cache is intentionally very short-lived.
            builder.withCacheConfiguration(
                    CacheNames.DRAFT_LIST_LATEST,
                    baseRedisCacheConfiguration(props.getDraftListTtl(), props.getKeyPrefix()).disableCachingNullValues()
            );

            // Status counts accept slight staleness in exchange for better read stability.
            builder.withCacheConfiguration(
                    CacheNames.DRAFT_STATUS_COUNT,
                    baseRedisCacheConfiguration(props.getDraftStatusCountTtl(), props.getKeyPrefix()).disableCachingNullValues()
            );
        };
    }

    /**
     * Redis 鎶栧姩鎴栫綉缁滃紓甯告椂锛岀紦瀛樿鍐欏け璐ヤ笉搴斿奖鍝嶄富涓氬姟銆?     *
     * <p>濡傛灉浣犳洿鍊惧悜 fail-fast锛屼篃鍙互鏀规垚鐩存帴鎶涘紓甯搞€?/p>
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("cache GET error. cacheName={}, key={}", safeName(cache), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("cache PUT error. cacheName={}, key={}", safeName(cache), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("cache EVICT error. cacheName={}, key={}", safeName(cache), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("cache CLEAR error. cacheName={}", safeName(cache), exception);
            }

            private String safeName(Cache cache) {
                return cache == null ? "null" : cache.getName();
            }
        };
    }

    private RedisCacheConfiguration baseRedisCacheConfiguration(Duration ttl, String keyPrefix) {
        // Use String keys and JSON values for better readability and polymorphic object support.
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .entryTtl(ttl == null ? Duration.ofMinutes(10) : ttl);

        // Apply a custom prefix when configured to isolate environments/services.
        if (keyPrefix != null && !keyPrefix.isBlank()) {
            cfg = cfg.computePrefixWith(cacheName -> keyPrefix + "cache:" + cacheName + ":");
        }
        return cfg;
    }

    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Preserve type information so cached polymorphic objects can be deserialized safely.
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}

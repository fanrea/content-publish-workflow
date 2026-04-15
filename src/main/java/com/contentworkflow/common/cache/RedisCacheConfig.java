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
 * Redis + Spring Cache 统一配置。
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>缓存只是加速层，默认采用 best-effort，Redis 异常不影响主流程。</li>
 *   <li>不同 cacheName 使用不同 TTL，避免一刀切。</li>
 *   <li>统一 JSON 序列化，兼容 LocalDateTime 等 Java 时间类型。</li>
 * </ul>
 *
 * <p>默认不强依赖 Redis；只有启用 `redis` profile 后，缓存类型才会切换为 Redis。</p>
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

            // 默认配置作为兜底值。
            builder.cacheDefaults(defaultCfg);

            // 草稿详情：短 TTL，且不缓存 null。
            builder.withCacheConfiguration(
                    CacheNames.DRAFT_DETAIL_BY_ID,
                    baseRedisCacheConfiguration(props.getDraftDetailTtl(), props.getKeyPrefix()).disableCachingNullValues()
            );
            builder.withCacheConfiguration(
                    CacheNames.DRAFT_DETAIL_BY_BIZ_NO,
                    baseRedisCacheConfiguration(props.getDraftDetailTtl(), props.getKeyPrefix()).disableCachingNullValues()
            );

            // 草稿列表：极短 TTL，仅用于演示或统计类页面，不代替分页查询。
            builder.withCacheConfiguration(
                    CacheNames.DRAFT_LIST_LATEST,
                    baseRedisCacheConfiguration(props.getDraftListTtl(), props.getKeyPrefix()).disableCachingNullValues()
            );

            // 状态计数：非常短的 TTL，接受轻微延迟以换取更稳定的读性能。
            builder.withCacheConfiguration(
                    CacheNames.DRAFT_STATUS_COUNT,
                    baseRedisCacheConfiguration(props.getDraftStatusCountTtl(), props.getKeyPrefix()).disableCachingNullValues()
            );
        };
    }

    /**
     * Redis 抖动或网络异常时，缓存读写失败不应影响主业务。
     *
     * <p>如果你更倾向 fail-fast，也可以改成直接抛异常。</p>
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
        // key 统一使用 String，value 使用 JSON，便于存储复杂对象。
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .entryTtl(ttl == null ? Duration.ofMinutes(10) : ttl);

        // 自定义前缀用于隔离不同环境或不同服务的 key 空间。
        if (keyPrefix != null && !keyPrefix.isBlank()) {
            cfg = cfg.computePrefixWith(cacheName -> keyPrefix + "cache:" + cacheName + ":");
        }
        return cfg;
    }

    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Redis 缓存值需要携带类型信息，避免反序列化时丢失具体类型。
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}

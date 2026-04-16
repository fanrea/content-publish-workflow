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
 * 配置类，用于声明当前模块运行所需的 Bean、策略或中间件集成设置。
 */
@EnableCaching
@Configuration
@EnableConfigurationProperties(WorkflowCacheProperties.class)
public class RedisCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    /**
     * 处理 redis cache manager builder customizer 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @return 方法处理后的结果对象
     */

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
     * 处理 cache error handler 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            /**
             * 处理 handle cache get error 相关逻辑，并返回对应的执行结果。
             *
             * @param exception 异常对象
             * @param cache 当前缓存对象
             * @param key 缓存或业务标识键
             */

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("cache GET error. cacheName={}, key={}", safeName(cache), key, exception);
            }

            /**
             * 处理 handle cache put error 相关逻辑，并返回对应的执行结果。
             *
             * @param exception 异常对象
             * @param cache 当前缓存对象
             * @param key 缓存或业务标识键
             * @param value 待处理的原始值
             */

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("cache PUT error. cacheName={}, key={}", safeName(cache), key, exception);
            }

            /**
             * 处理 handle cache evict error 相关逻辑，并返回对应的执行结果。
             *
             * @param exception 异常对象
             * @param cache 当前缓存对象
             * @param key 缓存或业务标识键
             */

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("cache EVICT error. cacheName={}, key={}", safeName(cache), key, exception);
            }

            /**
             * 处理 handle cache clear error 相关逻辑，并返回对应的执行结果。
             *
             * @param exception 异常对象
             * @param cache 当前缓存对象
             */

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("cache CLEAR error. cacheName={}", safeName(cache), exception);
            }

            /**
             * 处理 safe name 相关逻辑，并返回对应的执行结果。
             *
             * @param cache 当前缓存对象
             * @return 方法处理后的结果对象
             */

            private String safeName(Cache cache) {
                return cache == null ? "null" : cache.getName();
            }
        };
    }

    /**
     * 处理 base redis cache configuration 相关逻辑，并返回对应的执行结果。
     *
     * @param ttl 缓存有效期参数
     * @param keyPrefix 参数 keyPrefix 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 处理 redis object mapper 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */

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

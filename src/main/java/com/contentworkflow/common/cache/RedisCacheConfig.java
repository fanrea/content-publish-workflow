package com.contentworkflow.common.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@EnableCaching
@Configuration
@EnableConfigurationProperties(WorkflowCacheProperties.class)
public class RedisCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Bean
    public CacheManager cacheManager(WorkflowCacheProperties props,
                                     ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
                                     @Value("${spring.cache.type:simple}") String cacheType) {
        Map<String, WorkflowCacheSpec> cacheSpecs = WorkflowCacheSpec.builtin(props, DEFAULT_TTL);
        CacheManager localCacheManager = new LocalTierCacheManager(cacheSpecs);

        if (!"redis".equalsIgnoreCase(cacheType)) {
            log.info("workflow cache initialized with local tier only, spring.cache.type={}", cacheType);
            return localCacheManager;
        }

        RedisConnectionFactory redisConnectionFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (redisConnectionFactory == null) {
            log.warn("spring.cache.type=redis but RedisConnectionFactory is unavailable, falling back to local tier only");
            return localCacheManager;
        }

        CacheManager redisCacheManager = redisCacheManager(redisConnectionFactory, cacheSpecs, props);
        log.info("workflow cache initialized with local L1 and redis L2 tiers");
        return new TwoLevelCacheManager(localCacheManager, redisCacheManager, cacheSpecs);
    }

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

    private CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory,
                                           Map<String, WorkflowCacheSpec> cacheSpecs,
                                           WorkflowCacheProperties props) {
        RedisCacheConfiguration defaultConfiguration = baseRedisCacheConfiguration(DEFAULT_TTL, props.getKeyPrefix());
        Map<String, RedisCacheConfiguration> initialConfigurations = new LinkedHashMap<>();
        for (Map.Entry<String, WorkflowCacheSpec> entry : cacheSpecs.entrySet()) {
            initialConfigurations.put(
                    entry.getKey(),
                    baseRedisCacheConfiguration(entry.getValue().ttl(), props.getKeyPrefix()).disableCachingNullValues()
            );
        }
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfiguration.disableCachingNullValues())
                .withInitialCacheConfigurations(initialConfigurations)
                .transactionAware()
                .build();
    }

    private RedisCacheConfiguration baseRedisCacheConfiguration(Duration ttl, String keyPrefix) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .entryTtl(ttl == null ? DEFAULT_TTL : ttl)
                .disableCachingNullValues();

        if (keyPrefix != null && !keyPrefix.isBlank()) {
            configuration = configuration.computePrefixWith(cacheName -> keyPrefix + "cache:" + cacheName + ":");
        }
        return configuration;
    }

    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}

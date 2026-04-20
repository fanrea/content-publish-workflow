package com.contentworkflow.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

interface LocalTierCacheFactory {

    Cache create(String cacheName, WorkflowCacheSpec cacheSpec);

    static LocalTierCacheFactory create() {
        return ReflectiveCaffeineCacheFactory.isAvailable()
                ? new ReflectiveCaffeineCacheFactory()
                : new FallbackLocalTierCacheFactory();
    }
}

final class ReflectiveCaffeineCacheFactory implements LocalTierCacheFactory {

    private static final Logger log = LoggerFactory.getLogger(ReflectiveCaffeineCacheFactory.class);
    private static final String CAFFEINE_BUILDER = "com.github.benmanes.caffeine.cache.Caffeine";
    private static final String CAFFEINE_CACHE = "com.github.benmanes.caffeine.cache.Cache";
    private static final String SPRING_CAFFEINE_CACHE = "org.springframework.cache.caffeine.CaffeineCache";

    static boolean isAvailable() {
        return isPresent(CAFFEINE_BUILDER) && isPresent(CAFFEINE_CACHE) && isPresent(SPRING_CAFFEINE_CACHE);
    }

    @Override
    public Cache create(String cacheName, WorkflowCacheSpec cacheSpec) {
        try {
            Class<?> caffeineClass = Class.forName(CAFFEINE_BUILDER);
            Object builder = caffeineClass.getMethod("newBuilder").invoke(null);
            Method expireAfterWrite = builder.getClass().getMethod("expireAfterWrite", java.time.Duration.class);
            Method maximumSize = builder.getClass().getMethod("maximumSize", long.class);
            Method build = builder.getClass().getMethod("build");

            expireAfterWrite.invoke(builder, cacheSpec.ttl());
            maximumSize.invoke(builder, cacheSpec.maximumSize());
            Object nativeCache = build.invoke(builder);

            Class<?> nativeCacheType = Class.forName(CAFFEINE_CACHE);
            Class<?> springCacheType = Class.forName(SPRING_CAFFEINE_CACHE);
            Constructor<?> constructor = springCacheType.getConstructor(String.class, nativeCacheType, boolean.class);
            return (Cache) constructor.newInstance(cacheName, nativeCache, false);
        } catch (ReflectiveOperationException | LinkageError ex) {
            log.warn("caffeine local tier initialization failed for cache={}, falling back to in-process ttl cache", cacheName, ex);
            return new LocalTtlCache(cacheName, cacheSpec);
        }
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}

final class FallbackLocalTierCacheFactory implements LocalTierCacheFactory {

    @Override
    public Cache create(String cacheName, WorkflowCacheSpec cacheSpec) {
        return new LocalTtlCache(cacheName, cacheSpec);
    }
}

package com.contentworkflow.document.application.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RealtimeConfigurationBehaviorTest {

    @Test
    void noopBeansShouldBeAvailableWithDefaultConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(NoopConfig.class)
                .run(context -> {
                    assertThat(context.getBean(DocumentRealtimeRedisIndex.class))
                            .isInstanceOf(NoopDocumentRealtimeRedisIndex.class);
                    assertThat(context.getBean(DocumentRealtimeRecentUpdateCache.class))
                            .isInstanceOf(NoopDocumentRealtimeRecentUpdateCache.class);
                });
    }

    @Test
    void redisBeansShouldBeAvailableWhenFeatureFlagsEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisConfig.class)
                .withPropertyValues(
                        "workflow.realtime.redis-index.enabled=true",
                        "workflow.realtime.recent-updates.enabled=true",
                        "workflow.realtime.gateway-id=gw-test",
                        "workflow.realtime.recent-updates.size=50",
                        "workflow.realtime.recent-updates.ttl=60s"
                )
                .run(context -> {
                    assertThat(context.getBean(DocumentRealtimeRedisIndex.class))
                            .isInstanceOf(RedisDocumentRealtimeRedisIndex.class);
                    assertThat(context.getBean(DocumentRealtimeRecentUpdateCache.class))
                            .isInstanceOf(RedisDocumentRealtimeRecentUpdateCache.class);
                });
    }

    @Configuration
    @Import({
            NoopDocumentRealtimeRedisIndex.class,
            NoopDocumentRealtimeRecentUpdateCache.class
    })
    static class NoopConfig {
    }

    @Configuration
    static class RedisConfig {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        DocumentRealtimeRedisIndex documentRealtimeRedisIndex(StringRedisTemplate stringRedisTemplate) {
            return new RedisDocumentRealtimeRedisIndex(stringRedisTemplate, "gw-test");
        }

        @Bean
        DocumentRealtimeRecentUpdateCache documentRealtimeRecentUpdateCache(StringRedisTemplate stringRedisTemplate,
                                                                            ObjectMapper objectMapper) {
            return new RedisDocumentRealtimeRecentUpdateCache(
                    stringRedisTemplate,
                    objectMapper,
                    50,
                    Duration.ofSeconds(60),
                    true
            );
        }
    }
}

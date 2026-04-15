package com.contentworkflow.common.messaging.outbox;

import java.util.Locale;
import java.util.Objects;

/**
 * 默认 routingKey 规则：
 * - 前缀 + eventType 小写
 * - 下划线转点号，例如 CONTENT_PUBLISHED -> content.content.published
 *
 * <p>说明：这只是默认策略，实际项目可以按业务域拆分更细的路由规则。</p>
 */
public class UnderscoreToDotRoutingKeyResolver implements RoutingKeyResolver {

    private final String prefix;

    public UnderscoreToDotRoutingKeyResolver(String prefix) {
        this.prefix = (prefix == null ? "" : prefix);
    }

    @Override
    public String resolve(String eventType) {
        Objects.requireNonNull(eventType, "eventType");
        String normalized = eventType.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        return prefix + normalized;
    }
}


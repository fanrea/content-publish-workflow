package com.contentworkflow.common.messaging.outbox;

/**
 * routingKey 解析策略（扩展点）。
 */
public interface RoutingKeyResolver {

    String resolve(String eventType);
}


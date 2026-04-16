package com.contentworkflow.common.messaging.outbox;

import java.util.Locale;
import java.util.Objects;

/**
 * 解析器组件，用于把输入信息转换为业务流程可直接使用的结构化结果。
 */
public class UnderscoreToDotRoutingKeyResolver implements RoutingKeyResolver {

    private final String prefix;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param prefix 参数 prefix 对应的业务输入值
     */

    public UnderscoreToDotRoutingKeyResolver(String prefix) {
        this.prefix = (prefix == null ? "" : prefix);
    }

    /**
     * 解析输入信息并生成当前流程所需的结构化结果。
     *
     * @param eventType 参数 eventType 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Override
    public String resolve(String eventType) {
        Objects.requireNonNull(eventType, "eventType");
        String normalized = eventType.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        return prefix + normalized;
    }
}


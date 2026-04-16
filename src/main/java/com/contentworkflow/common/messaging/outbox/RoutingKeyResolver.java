package com.contentworkflow.common.messaging.outbox;

/**
 * 解析器组件，用于把输入信息转换为业务流程可直接使用的结构化结果。
 */
public interface RoutingKeyResolver {

    /**
     * 解析输入信息并生成当前流程所需的结构化结果。
     *
     * @param eventType 参数 eventType 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    String resolve(String eventType);
}


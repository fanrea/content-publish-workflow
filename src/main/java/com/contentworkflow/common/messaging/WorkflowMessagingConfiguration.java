package com.contentworkflow.common.messaging;

import com.contentworkflow.common.messaging.outbox.NoopWorkflowEventPublisher;
import com.contentworkflow.common.messaging.outbox.OutboxWorkflowEventPublisher;
import com.contentworkflow.common.messaging.outbox.RoutingKeyResolver;
import com.contentworkflow.common.messaging.outbox.UnderscoreToDotRoutingKeyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于声明当前模块运行所需的 Bean、策略或中间件集成设置。
 */
@Configuration
@EnableConfigurationProperties(WorkflowMessagingProperties.class)
public class WorkflowMessagingConfiguration {

    /**
     * 处理 routing key resolver 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @return 方法处理后的结果对象
     */

    @Bean
    @ConditionalOnMissingBean
    public RoutingKeyResolver routingKeyResolver(WorkflowMessagingProperties props) {
        return new UnderscoreToDotRoutingKeyResolver(props.getRoutingKeyPrefix());
    }

    /**
     * 处理 outbox workflow event publisher 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @param routingKeyResolver 参数 routingKeyResolver 对应的业务输入值
     * @param objectMapper 参数 objectMapper 对应的业务输入值
     * @param enqueuer 参数 enqueuer 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Bean
    @ConditionalOnProperty(prefix = "workflow.outbox", name = "enabled", havingValue = "true")
    public WorkflowEventPublisher outboxWorkflowEventPublisher(WorkflowMessagingProperties props,
                                                               RoutingKeyResolver routingKeyResolver,
                                                               ObjectMapper objectMapper,
                                                               com.contentworkflow.common.messaging.outbox.OutboxEnqueuer enqueuer) {
        return new OutboxWorkflowEventPublisher(props, routingKeyResolver, objectMapper, enqueuer);
    }

    /**
     * 处理 noop workflow event publisher 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */

    @Bean
    @ConditionalOnMissingBean(WorkflowEventPublisher.class)
    public WorkflowEventPublisher noopWorkflowEventPublisher() {
        return new NoopWorkflowEventPublisher();
    }
}

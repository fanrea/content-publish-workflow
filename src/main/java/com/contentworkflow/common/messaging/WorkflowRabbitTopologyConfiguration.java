package com.contentworkflow.common.messaging;

import com.contentworkflow.common.messaging.outbox.RoutingKeyResolver;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于声明当前模块运行所需的 Bean、策略或中间件集成设置。
 */
@Configuration
@ConditionalOnProperty(prefix = "workflow.outbox.topology", name = "enabled", havingValue = "true")
public class WorkflowRabbitTopologyConfiguration {

    /**
     * 处理 workflow event exchange 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @return 方法处理后的结果对象
     */

    @Bean
    public TopicExchange workflowEventExchange(WorkflowMessagingProperties props) {
        return new TopicExchange(props.getExchange(), true, false);
    }

    /**
     * 处理 workflow search index queue 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @return 方法处理后的结果对象
     */

    @Bean
    public Queue workflowSearchIndexQueue(WorkflowMessagingProperties props) {
        return new Queue(props.getTopology().getSearchIndexQueue(), true);
    }

    /**
     * 处理 workflow read model queue 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @return 方法处理后的结果对象
     */

    @Bean
    public Queue workflowReadModelQueue(WorkflowMessagingProperties props) {
        return new Queue(props.getTopology().getReadModelQueue(), true);
    }

    /**
     * 处理 workflow notification queue 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @return 方法处理后的结果对象
     */

    @Bean
    public Queue workflowNotificationQueue(WorkflowMessagingProperties props) {
        return new Queue(props.getTopology().getNotificationQueue(), true);
    }

    /**
     * 处理 workflow rabbit declarables 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @param routingKeyResolver 参数 routingKeyResolver 对应的业务输入值
     * @param workflowEventExchange 参数 workflowEventExchange 对应的业务输入值
     * @param workflowSearchIndexQueue 参数 workflowSearchIndexQueue 对应的业务输入值
     * @param workflowReadModelQueue 参数 workflowReadModelQueue 对应的业务输入值
     * @param workflowNotificationQueue 参数 workflowNotificationQueue 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Bean
    public Declarables workflowRabbitDeclarables(WorkflowMessagingProperties props,
                                                 RoutingKeyResolver routingKeyResolver,
                                                 TopicExchange workflowEventExchange,
                                                 Queue workflowSearchIndexQueue,
                                                 Queue workflowReadModelQueue,
                                                 Queue workflowNotificationQueue) {
        Binding searchIndexBinding = BindingBuilder.bind(workflowSearchIndexQueue)
                .to(workflowEventExchange)
                .with(routingKeyResolver.resolve(WorkflowEventTypes.SEARCH_INDEX_REFRESH_REQUESTED));
        Binding readModelBinding = BindingBuilder.bind(workflowReadModelQueue)
                .to(workflowEventExchange)
                .with(routingKeyResolver.resolve(WorkflowEventTypes.DOWNSTREAM_READ_MODEL_SYNC_REQUESTED));
        Binding notificationBinding = BindingBuilder.bind(workflowNotificationQueue)
                .to(workflowEventExchange)
                .with(routingKeyResolver.resolve(WorkflowEventTypes.PUBLISH_NOTIFICATION_REQUESTED));

        return new Declarables(
                workflowEventExchange,
                workflowSearchIndexQueue,
                workflowReadModelQueue,
                workflowNotificationQueue,
                searchIndexBinding,
                readModelBinding,
                notificationBinding
        );
    }

    /**
     * 处理 workflow rabbit admin 相关逻辑，并返回对应的执行结果。
     *
     * @param connectionFactory 参数 connectionFactory 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Bean
    public RabbitAdmin workflowRabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }
}

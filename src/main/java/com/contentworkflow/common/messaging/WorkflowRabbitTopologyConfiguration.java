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
 * RabbitMQ topology for publish side-effect events.
 */
@Configuration
@ConditionalOnProperty(prefix = "workflow.outbox.topology", name = "enabled", havingValue = "true")
public class WorkflowRabbitTopologyConfiguration {

    @Bean
    public TopicExchange workflowEventExchange(WorkflowMessagingProperties props) {
        return new TopicExchange(props.getExchange(), true, false);
    }

    @Bean
    public Queue workflowSearchIndexQueue(WorkflowMessagingProperties props) {
        return new Queue(props.getTopology().getSearchIndexQueue(), true);
    }

    @Bean
    public Queue workflowReadModelQueue(WorkflowMessagingProperties props) {
        return new Queue(props.getTopology().getReadModelQueue(), true);
    }

    @Bean
    public Queue workflowNotificationQueue(WorkflowMessagingProperties props) {
        return new Queue(props.getTopology().getNotificationQueue(), true);
    }

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

    @Bean
    public RabbitAdmin workflowRabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }
}

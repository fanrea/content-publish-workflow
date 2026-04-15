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
 * Messaging/outbox wiring.
 *
 * <p>Default: a no-op publisher is always available so business code can safely inject it.
 * When {@code workflow.outbox.enabled=true}, the publisher switches to the outbox implementation.</p>
 */
@Configuration
@EnableConfigurationProperties(WorkflowMessagingProperties.class)
public class WorkflowMessagingConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RoutingKeyResolver routingKeyResolver(WorkflowMessagingProperties props) {
        return new UnderscoreToDotRoutingKeyResolver(props.getRoutingKeyPrefix());
    }

    @Bean
    @ConditionalOnProperty(prefix = "workflow.outbox", name = "enabled", havingValue = "true")
    public WorkflowEventPublisher outboxWorkflowEventPublisher(WorkflowMessagingProperties props,
                                                               RoutingKeyResolver routingKeyResolver,
                                                               ObjectMapper objectMapper,
                                                               com.contentworkflow.common.messaging.outbox.OutboxEnqueuer enqueuer) {
        return new OutboxWorkflowEventPublisher(props, routingKeyResolver, objectMapper, enqueuer);
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowEventPublisher.class)
    public WorkflowEventPublisher noopWorkflowEventPublisher() {
        return new NoopWorkflowEventPublisher();
    }
}

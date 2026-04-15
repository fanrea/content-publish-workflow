package com.contentworkflow.workflow.application.task;

import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Default publish task handlers.
 *
 * <p>Handlers turn accepted publish tasks into MQ-facing workflow events. Downstream systems can
 * consume those events asynchronously without stretching the main publish transaction.</p>
 */
@Configuration
public class DefaultPublishTaskHandlers {

    @Bean
    public PublishTaskHandler refreshSearchIndexTaskHandler() {
        return eventPublishingHandler(
                PublishTaskType.REFRESH_SEARCH_INDEX,
                ctx -> ctx.eventPublisher().publish(PublishTaskEventFactory.buildSearchIndexRefreshRequestedEvent(ctx))
        );
    }

    @Bean
    public PublishTaskHandler syncDownstreamReadModelTaskHandler() {
        return eventPublishingHandler(
                PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL,
                ctx -> ctx.eventPublisher().publish(PublishTaskEventFactory.buildReadModelSyncRequestedEvent(ctx))
        );
    }

    @Bean
    public PublishTaskHandler sendPublishNotificationTaskHandler() {
        return eventPublishingHandler(
                PublishTaskType.SEND_PUBLISH_NOTIFICATION,
                ctx -> ctx.eventPublisher().publish(PublishTaskEventFactory.buildPublishNotificationRequestedEvent(ctx))
        );
    }

    private PublishTaskHandler eventPublishingHandler(PublishTaskType taskType, Consumer<PublishTaskContext> executor) {
        return new PublishTaskHandler() {
            @Override
            public PublishTaskType taskType() {
                return taskType;
            }

            @Override
            public void execute(PublishTaskContext ctx) {
                executor.accept(ctx);
            }

            @Override
            public void compensate(PublishTaskContext ctx) {
                // Downstream compensation is best-effort and should be owned by the consumer side.
            }
        };
    }
}

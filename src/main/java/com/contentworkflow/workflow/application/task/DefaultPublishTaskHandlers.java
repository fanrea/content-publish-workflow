package com.contentworkflow.workflow.application.task;

import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * 处理器组件，负责承接特定工作流节点、任务或调度场景的执行逻辑。
 */
@Configuration
public class DefaultPublishTaskHandlers {

    /**
     * 刷新相关状态或缓存数据，确保后续读取到最新结果。
     *
     * @return 方法处理后的结果对象
     */

    @Bean
    public PublishTaskHandler refreshSearchIndexTaskHandler() {
        return eventPublishingHandler(
                PublishTaskType.REFRESH_SEARCH_INDEX,
                ctx -> ctx.eventPublisher().publish(PublishTaskEventFactory.buildSearchIndexRefreshRequestedEvent(ctx))
        );
    }

    /**
     * 同步相关数据状态，保持不同系统或模型之间的一致性。
     *
     * @return 方法处理后的结果对象
     */

    @Bean
    public PublishTaskHandler syncDownstreamReadModelTaskHandler() {
        return eventPublishingHandler(
                PublishTaskType.SYNC_DOWNSTREAM_READ_MODEL,
                ctx -> ctx.eventPublisher().publish(PublishTaskEventFactory.buildReadModelSyncRequestedEvent(ctx))
        );
    }

    /**
     * 处理 send publish notification task handler 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */

    @Bean
    public PublishTaskHandler sendPublishNotificationTaskHandler() {
        return eventPublishingHandler(
                PublishTaskType.SEND_PUBLISH_NOTIFICATION,
                ctx -> ctx.eventPublisher().publish(PublishTaskEventFactory.buildPublishNotificationRequestedEvent(ctx))
        );
    }

    /**
     * 处理 event publishing handler 相关逻辑，并返回对应的执行结果。
     *
     * @param taskType 参数 taskType 对应的业务输入值
     * @param executor 参数 executor 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private PublishTaskHandler eventPublishingHandler(PublishTaskType taskType, Consumer<PublishTaskContext> executor) {
        return new PublishTaskHandler() {
            /**
             * 处理 task type 相关逻辑，并返回对应的执行结果。
             *
             * @return 方法处理后的结果对象
             */

            @Override
            public PublishTaskType taskType() {
                return taskType;
            }

            /**
             * 处理 execute 相关逻辑，并返回对应的执行结果。
             *
             * @param ctx 参数 ctx 对应的业务输入值
             */

            @Override
            public void execute(PublishTaskContext ctx) {
                executor.accept(ctx);
            }

            /**
             * 处理 compensate 相关逻辑，并返回对应的执行结果。
             *
             * @param ctx 参数 ctx 对应的业务输入值
             */

            @Override
            public void compensate(PublishTaskContext ctx) {
                // Downstream compensation is best-effort and should be owned by the consumer side.
            }
        };
    }
}

package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于声明当前模块运行所需的 Bean、策略或中间件集成设置。
 */
@Configuration
public class WorkflowSideEffectGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSideEffectGatewayConfiguration.class);

    /**
     * 处理 search index refresh gateway 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */

    @Bean
    @ConditionalOnMissingBean
    public SearchIndexRefreshGateway searchIndexRefreshGateway() {
        return payload -> log.info("search-index gateway accepted draftId={} version={} bizNo={}",
                payload.draftId(), payload.publishedVersion(), payload.bizNo());
    }

    /**
     * 处理 read model sync gateway 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */

    @Bean
    @ConditionalOnMissingBean
    public ReadModelSyncGateway readModelSyncGateway() {
        return payload -> log.info("read-model gateway accepted draftId={} version={} bizNo={}",
                payload.draftId(), payload.publishedVersion(), payload.bizNo());
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @return 方法处理后的结果对象
     */

    @Bean
    @ConditionalOnMissingBean
    public PublishNotificationGateway publishNotificationGateway() {
        return payload -> log.info("notification gateway accepted draftId={} version={} title={}",
                payload.draftId(), payload.publishedVersion(), payload.title());
    }
}

package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default gateway wiring for MQ consumer side effects.
 *
 * <p>Real projects can replace these beans with Elasticsearch clients, HTTP adapters, or internal
 * service SDKs. Default implementations keep this service runnable while preserving clear
 * integration seams.</p>
 */
@Configuration
public class WorkflowSideEffectGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSideEffectGatewayConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SearchIndexRefreshGateway searchIndexRefreshGateway() {
        return payload -> log.info("search-index gateway accepted draftId={} version={} bizNo={}",
                payload.draftId(), payload.publishedVersion(), payload.bizNo());
    }

    @Bean
    @ConditionalOnMissingBean
    public ReadModelSyncGateway readModelSyncGateway() {
        return payload -> log.info("read-model gateway accepted draftId={} version={} bizNo={}",
                payload.draftId(), payload.publishedVersion(), payload.bizNo());
    }

    @Bean
    @ConditionalOnMissingBean
    public PublishNotificationGateway publishNotificationGateway() {
        return payload -> log.info("notification gateway accepted draftId={} version={} title={}",
                payload.draftId(), payload.publishedVersion(), payload.title());
    }
}

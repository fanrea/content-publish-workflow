package com.contentworkflow.workflow.application.task;

import com.contentworkflow.common.messaging.WorkflowEventPublisher;
import com.contentworkflow.common.messaging.WorkflowMessagingProperties;
import com.contentworkflow.common.messaging.outbox.OutboxEventRepository;
import com.contentworkflow.common.scheduler.XxlJobExecutorConfiguration;
import com.contentworkflow.workflow.application.store.WorkflowStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class WorkflowSchedulerSwitchTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(XxlJobExecutorConfiguration.class, WorkflowXxlJobHandlers.class, StubDependencies.class);

    @Test
    void publishTaskWorker_shouldSkipScheduledPollWhenLocalSchedulerDisabled() {
        CountingPublishTaskWorker worker = new CountingPublishTaskWorker();
        ReflectionTestUtils.setField(worker, "localScheduleEnabled", false);

        worker.scheduledPollOnce();

        assertEquals(0, worker.pollCount);
    }

    @Test
    void publishTaskWorker_shouldRunScheduledPollWhenLocalSchedulerEnabled() {
        CountingPublishTaskWorker worker = new CountingPublishTaskWorker();
        ReflectionTestUtils.setField(worker, "localScheduleEnabled", true);

        worker.scheduledPollOnce();

        assertEquals(1, worker.pollCount);
    }

    @Test
    void outboxRelayWorker_shouldSkipScheduledPollWhenLocalSchedulerDisabled() {
        CountingOutboxRelayWorker worker = new CountingOutboxRelayWorker();
        ReflectionTestUtils.setField(worker, "localScheduleEnabled", false);

        worker.scheduledPollOnce();

        assertEquals(0, worker.pollCount);
    }

    @Test
    void outboxRelayWorker_shouldRunScheduledPollWhenLocalSchedulerEnabled() {
        CountingOutboxRelayWorker worker = new CountingOutboxRelayWorker();
        ReflectionTestUtils.setField(worker, "localScheduleEnabled", true);

        worker.scheduledPollOnce();

        assertEquals(1, worker.pollCount);
    }

    @Test
    void xxlJobBeans_shouldBeDisabledByDefault() {
        contextRunner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(XxlJobSpringExecutor.class);
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(WorkflowXxlJobHandlers.class);
        });
    }

    @Test
    void xxlJobBeans_shouldLoadWhenExecutorIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "xxl.job.executor.enabled=true",
                        "xxl.job.admin.addresses=http://127.0.0.1:8080/xxl-job-admin",
                        "xxl.job.admin.access-token=test-token",
                        "xxl.job.executor.appname=content-publish-workflow"
                )
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(XxlJobSpringExecutor.class);
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(WorkflowXxlJobHandlers.class);
                });
    }

    private static final class CountingPublishTaskWorker extends PublishTaskWorker {

        private int pollCount;

        private CountingPublishTaskWorker() {
            super(
                    mock(WorkflowStore.class),
                    List.of(),
                    mock(WorkflowEventPublisher.class),
                    mock(PublishTaskProgressService.class)
            );
        }

        @Override
        public void pollOnce() {
            pollCount++;
        }
    }

    private static final class CountingOutboxRelayWorker extends OutboxRelayWorker {

        private int pollCount;

        private CountingOutboxRelayWorker() {
            super(
                    mock(OutboxEventRepository.class),
                    mock(RabbitTemplate.class),
                    new ObjectMapper(),
                    new WorkflowMessagingProperties()
            );
        }

        @Override
        public void pollOnce() {
            pollCount++;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StubDependencies {

        @Bean
        PublishTaskWorker publishTaskWorker() {
            return mock(PublishTaskWorker.class);
        }

        @Bean
        OutboxRelayWorker outboxRelayWorker() {
            return mock(OutboxRelayWorker.class);
        }

        @Bean
        WorkflowReconciliationService workflowReconciliationService() {
            return mock(WorkflowReconciliationService.class);
        }
    }
}

package com.contentworkflow.common.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class WorkflowSchedulingTraceConfiguration implements SchedulingConfigurer {

    @Bean(destroyMethod = "destroy")
    public TracingThreadPoolTaskScheduler workflowTracingTaskScheduler() {
        TracingThreadPoolTaskScheduler scheduler = new TracingThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("workflow-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(workflowTracingTaskScheduler());
    }
}

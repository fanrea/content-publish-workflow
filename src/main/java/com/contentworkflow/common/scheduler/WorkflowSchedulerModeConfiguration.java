package com.contentworkflow.common.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties({WorkflowSchedulerProperties.class, XxlJobProperties.class})
public class WorkflowSchedulerModeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSchedulerModeConfiguration.class);

    @Bean
    public ApplicationRunner workflowSchedulerModeReporter(WorkflowSchedulerProperties schedulerProperties,
                                                           XxlJobProperties xxlJobProperties,
                                                           Environment environment) {
        return args -> {
            if (!schedulerProperties.isStartupReportEnabled()) {
                return;
            }

            WorkflowSchedulerTraceContext.runStartup("workflow-scheduler-mode-reporter", () -> {
                boolean localEnabled = schedulerProperties.getLocal().isEnabled();
                boolean xxlEnabled = xxlJobProperties.getExecutor().isEnabled();
                WorkflowSchedulerProperties.TriggerMode mode = schedulerProperties.resolveTriggerMode(xxlEnabled);
                String profiles = Arrays.stream(environment.getActiveProfiles()).collect(Collectors.joining(","));
                if (profiles.isBlank()) {
                    profiles = "<none>";
                }

                switch (mode) {
                    case LOCAL -> log.info(
                            "Workflow scheduler mode=LOCAL_FALLBACK, activeProfiles={}, localScheduled=true, xxlExecutor=false. " +
                                    "This mode is for local development and smoke tests; production recommendation is {}.",
                            profiles,
                            schedulerProperties.getProductionRecommended()
                    );
                    case XXL_JOB -> log.info(
                            "Workflow scheduler mode=XXL_JOB, activeProfiles={}, localScheduled=false, xxlExecutor=true. " +
                                    "Task polling is expected to be triggered by XXL-Job handlers.",
                            profiles
                    );
                    case HYBRID -> log.warn(
                            "Workflow scheduler mode=HYBRID, activeProfiles={}, localScheduled=true, xxlExecutor=true. " +
                                    "Both @Scheduled and XXL-Job can trigger the same workers; disable workflow.scheduler.local.enabled in XXL-Job environments.",
                            profiles
                    );
                    case NONE -> log.warn(
                            "Workflow scheduler mode=NONE, activeProfiles={}, localScheduled=false, xxlExecutor=false. " +
                            "No polling trigger is active; enable workflow.scheduler.local.enabled for local fallback or xxl.job.executor.enabled for scheduler-center mode.",
                            profiles
                    );
                }
            });
        };
    }
}

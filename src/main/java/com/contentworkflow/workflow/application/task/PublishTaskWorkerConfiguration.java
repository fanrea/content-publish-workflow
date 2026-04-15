package com.contentworkflow.workflow.application.task;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduling for the publish task worker.
 *
 * <p>The application entry point does not enable scheduling globally. This dedicated configuration
 * enables it for this module without affecting other modules.</p>
 */
@Configuration
@EnableScheduling
public class PublishTaskWorkerConfiguration {
}

package com.contentworkflow.workflow.application.task;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 配置类，用于声明当前模块运行所需的 Bean、策略或中间件集成设置。
 */
@Configuration
@EnableScheduling
public class PublishTaskWorkerConfiguration {
}

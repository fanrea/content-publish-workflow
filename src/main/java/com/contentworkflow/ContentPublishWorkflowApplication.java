package com.contentworkflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 内容发布工作流服务的 Spring Boot 启动入口，负责初始化容器和基础设施组件。
 */

@SpringBootApplication
public class ContentPublishWorkflowApplication {

    /**
     * 应用程序主方法，用于启动当前服务并初始化 Spring 容器。
     *
     * @param args 启动参数数组
     */

    public static void main(String[] args) {
        SpringApplication.run(ContentPublishWorkflowApplication.class, args);
    }
}

package com.contentworkflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ContentPublishWorkflowApplication 类，负责当前模块的业务实现。
 */

@SpringBootApplication
public class ContentPublishWorkflowApplication {

    /**
     * 处理 main 相关业务逻辑。
     * @param args 参数 args。
     */

    public static void main(String[] args) {
        SpringApplication.run(ContentPublishWorkflowApplication.class, args);
    }
}

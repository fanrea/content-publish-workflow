package com.contentworkflow.common.scheduler;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于声明当前模块运行所需的 Bean、策略或中间件集成设置。
 */
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
@ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true")
public class XxlJobExecutorConfiguration {

    /**
     * 处理 xxl job spring executor 相关逻辑，并返回对应的执行结果。
     *
     * @param props 配置属性对象
     * @return 方法处理后的结果对象
     */

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties props) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(props.getAdmin().getAddresses());
        executor.setAccessToken(props.getAdmin().getAccessToken());
        executor.setTimeout(props.getAdmin().getTimeout());
        executor.setAppname(props.getExecutor().getAppname());
        executor.setAddress(props.getExecutor().getAddress());
        executor.setIp(props.getExecutor().getIp());
        executor.setPort(props.getExecutor().getPort());
        executor.setLogPath(props.getExecutor().getLogpath());
        executor.setLogRetentionDays(props.getExecutor().getLogretentiondays());
        return executor;
    }
}

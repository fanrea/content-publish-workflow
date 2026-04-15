package com.contentworkflow.common.scheduler;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job executor bootstrap configuration.
 */
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
@ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true")
public class XxlJobExecutorConfiguration {

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

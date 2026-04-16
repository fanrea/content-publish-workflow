package com.contentworkflow.common.web.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
@Configuration
public class WorkflowWebMvcConfigurer implements WebMvcConfigurer {

    private final WorkflowAuthorizationInterceptor authorizationInterceptor;
    private final CurrentWorkflowOperatorArgumentResolver currentWorkflowOperatorArgumentResolver;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param authorizationInterceptor 参数 authorizationInterceptor 对应的业务输入值
     * @param currentWorkflowOperatorArgumentResolver 参数 currentWorkflowOperatorArgumentResolver 对应的业务输入值
     */

    public WorkflowWebMvcConfigurer(WorkflowAuthorizationInterceptor authorizationInterceptor,
                                    CurrentWorkflowOperatorArgumentResolver currentWorkflowOperatorArgumentResolver) {
        this.authorizationInterceptor = authorizationInterceptor;
        this.currentWorkflowOperatorArgumentResolver = currentWorkflowOperatorArgumentResolver;
    }

    /**
     * 处理 add interceptors 相关逻辑，并返回对应的执行结果。
     *
     * @param registry 参数 registry 对应的业务输入值
     */

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/api/workflows/**");
    }

    /**
     * 处理 add argument resolvers 相关逻辑，并返回对应的执行结果。
     *
     * @param resolvers 参数 resolvers 对应的业务输入值
     */

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentWorkflowOperatorArgumentResolver);
    }
}

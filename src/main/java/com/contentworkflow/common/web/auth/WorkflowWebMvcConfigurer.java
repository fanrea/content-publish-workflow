package com.contentworkflow.common.web.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Register workflow web infrastructure.
 */
@Configuration
public class WorkflowWebMvcConfigurer implements WebMvcConfigurer {

    private final WorkflowAuthorizationInterceptor authorizationInterceptor;
    private final CurrentWorkflowOperatorArgumentResolver currentWorkflowOperatorArgumentResolver;

    public WorkflowWebMvcConfigurer(WorkflowAuthorizationInterceptor authorizationInterceptor,
                                    CurrentWorkflowOperatorArgumentResolver currentWorkflowOperatorArgumentResolver) {
        this.authorizationInterceptor = authorizationInterceptor;
        this.currentWorkflowOperatorArgumentResolver = currentWorkflowOperatorArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/api/workflows/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentWorkflowOperatorArgumentResolver);
    }
}

package com.contentworkflow.common.web.auth;

import com.contentworkflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolve the current operator from request attributes populated by the auth interceptor.
 */
@Component
public class CurrentWorkflowOperatorArgumentResolver implements HandlerMethodArgumentResolver {

    private final WorkflowOperatorResolver operatorResolver;

    public CurrentWorkflowOperatorArgumentResolver(WorkflowOperatorResolver operatorResolver) {
        this.operatorResolver = operatorResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentWorkflowOperator.class)
                && WorkflowOperatorIdentity.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new BusinessException("UNAUTHORIZED", "missing http request context");
        }
        Object identity = request.getAttribute(WorkflowAuthConstants.REQUEST_OPERATOR_ATTR);
        if (identity instanceof WorkflowOperatorIdentity operatorIdentity) {
            return operatorIdentity;
        }

        // 兜底：在 standalone 测试或拦截器未注册的场景，仍可从 Header 解析操作人。
        WorkflowOperatorResolver.ResolvedWorkflowAuth auth = operatorResolver.resolveRequired(request);
        request.setAttribute(WorkflowAuthConstants.REQUEST_ROLES_ATTR, auth.roles());
        request.setAttribute(WorkflowAuthConstants.REQUEST_PERMISSIONS_ATTR, auth.permissions());
        request.setAttribute(WorkflowAuthConstants.REQUEST_ROLE_ATTR, auth.identity().role());
        request.setAttribute(WorkflowAuthConstants.REQUEST_OPERATOR_ATTR, auth.identity());
        return auth.identity();
    }
}

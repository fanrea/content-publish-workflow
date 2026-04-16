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
 * 解析器组件，用于把输入信息转换为业务流程可直接使用的结构化结果。
 */
@Component
public class CurrentWorkflowOperatorArgumentResolver implements HandlerMethodArgumentResolver {

    private final WorkflowOperatorResolver operatorResolver;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param operatorResolver 参数 operatorResolver 对应的业务输入值
     */

    public CurrentWorkflowOperatorArgumentResolver(WorkflowOperatorResolver operatorResolver) {
        this.operatorResolver = operatorResolver;
    }

    /**
     * 处理 supports parameter 相关逻辑，并返回对应的执行结果。
     *
     * @param parameter 参数 parameter 对应的业务输入值
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentWorkflowOperator.class)
                && WorkflowOperatorIdentity.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * 解析输入信息并生成当前流程所需的结构化结果。
     *
     * @param parameter 参数 parameter 对应的业务输入值
     * @param mavContainer 参数 mavContainer 对应的业务输入值
     * @param webRequest 封装业务输入的请求对象
     * @param binderFactory 参数 binderFactory 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

        // Fallback for standalone tests or scenarios where the interceptor is not installed.
        WorkflowOperatorResolver.ResolvedWorkflowAuth auth = operatorResolver.resolveRequired(request);
        request.setAttribute(WorkflowAuthConstants.REQUEST_ROLES_ATTR, auth.roles());
        request.setAttribute(WorkflowAuthConstants.REQUEST_PERMISSIONS_ATTR, auth.permissions());
        request.setAttribute(WorkflowAuthConstants.REQUEST_ROLE_ATTR, auth.identity().role());
        request.setAttribute(WorkflowAuthConstants.REQUEST_OPERATOR_ATTR, auth.identity());
        return auth.identity();
    }
}

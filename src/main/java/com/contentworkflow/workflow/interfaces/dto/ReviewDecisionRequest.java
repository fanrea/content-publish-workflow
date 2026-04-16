package com.contentworkflow.workflow.interfaces.dto;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 接口层请求模型，用于封装客户端输入参数并承载校验约束。
 */

public record ReviewDecisionRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 500) String comment
) {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param reviewer 参数 reviewer 对应的业务输入值
     * @param decision 参数 decision 对应的业务输入值
     * @param comment 参数 comment 对应的业务输入值
     */
    public ReviewDecisionRequest(String reviewer, ReviewDecision decision, String comment) {
        this(decision, comment);
    }
}

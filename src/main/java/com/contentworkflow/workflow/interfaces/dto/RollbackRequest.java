package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 接口层请求模型，用于封装客户端输入参数并承载校验约束。
 */

public record RollbackRequest(
        @NotNull @Min(1) Integer targetPublishedVersion,
        @Size(max = 500) String reason
) {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param operator 当前操作人身份信息
     * @param targetPublishedVersion 参数 targetPublishedVersion 对应的业务输入值
     * @param reason 参数 reason 对应的业务输入值
     */
    public RollbackRequest(String operator, Integer targetPublishedVersion, String reason) {
        this(targetPublishedVersion, reason);
    }
}

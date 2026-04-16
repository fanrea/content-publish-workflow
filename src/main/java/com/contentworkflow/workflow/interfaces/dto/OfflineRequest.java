package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.Size;

/**
 * 接口层请求模型，用于封装客户端输入参数并承载校验约束。
 */

public record OfflineRequest(
        @Size(max = 500) String remark
) {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param operator 当前操作人身份信息
     * @param remark 参数 remark 对应的业务输入值
     */
    public OfflineRequest(String operator, String remark) {
        this(remark);
    }
}

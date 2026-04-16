package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.Size;

/**
 * 接口层请求模型，用于封装客户端输入参数并承载校验约束。
 */
public record ManualRecoveryRequest(
        @Size(max = 500) String remark
) {
}

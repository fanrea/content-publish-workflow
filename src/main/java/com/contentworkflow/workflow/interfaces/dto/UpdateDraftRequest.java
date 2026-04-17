package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 接口层请求模型，用于封装客户端输入参数并承载校验约束。
 */

public record UpdateDraftRequest(
        @NotNull Long expectedVersion,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 500) String summary,
        @NotBlank String body
) {
    public UpdateDraftRequest(String title, String summary, String body) {
        this(null, title, summary, body);
    }
}

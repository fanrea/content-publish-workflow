package com.contentworkflow.document.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * CreateDocumentRequest 请求对象，用于封装接口入参。
 */
public record CreateDocumentRequest(
        String docNo,
        @NotBlank(message = "title must not be blank")
        String title,
        @NotNull(message = "content must not be null")
        String content
) {
}

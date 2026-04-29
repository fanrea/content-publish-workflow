package com.contentworkflow.document.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * UpdateDocumentRequest 请求对象，用于封装接口入参。
 */
public record UpdateDocumentRequest(
        @NotNull(message = "baseRevision must not be null")
        @Min(value = 1, message = "baseRevision must be >= 1")
        Integer baseRevision,
        @NotBlank(message = "title must not be blank")
        String title,
        @NotNull(message = "content must not be null")
        String content,
        String changeSummary
) {
}

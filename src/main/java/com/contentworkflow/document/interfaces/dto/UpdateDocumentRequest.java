package com.contentworkflow.document.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * UpdateDocumentRequest 请求对象，用于封装接口入参。
 */
public record UpdateDocumentRequest(
        Integer expectedRevision,
        Integer baseRevision,
        @NotBlank(message = "title must not be blank")
        String title,
        @NotNull(message = "content must not be null")
        String content,
        String changeSummary
) {
}

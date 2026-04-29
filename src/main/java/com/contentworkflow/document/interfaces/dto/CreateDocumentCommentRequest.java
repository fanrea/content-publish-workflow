package com.contentworkflow.document.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建评论请求。
 */
public record CreateDocumentCommentRequest(
        @NotNull(message = "baseRevision must not be null")
        @Min(value = 1, message = "baseRevision must be >= 1")
        Integer baseRevision,
        @NotNull(message = "startOffset must not be null")
        @Min(value = 0, message = "startOffset must be >= 0")
        Integer startOffset,
        @NotNull(message = "endOffset must not be null")
        @Min(value = 0, message = "endOffset must be >= 0")
        Integer endOffset,
        @NotBlank(message = "content must not be blank")
        String content
) {
}

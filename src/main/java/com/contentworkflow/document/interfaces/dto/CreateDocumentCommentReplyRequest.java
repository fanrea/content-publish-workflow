package com.contentworkflow.document.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 创建评论回复请求。
 */
public record CreateDocumentCommentReplyRequest(
        @Positive(message = "replyToReplyId must be > 0")
        Long replyToReplyId,
        @NotBlank(message = "content must not be blank")
        String content
) {
}

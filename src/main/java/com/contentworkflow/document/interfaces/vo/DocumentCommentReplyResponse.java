package com.contentworkflow.document.interfaces.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论回复响应对象。
 */
public record DocumentCommentReplyResponse(
        Long id,
        Long documentId,
        Long commentId,
        Long replyToReplyId,
        String content,
        List<String> mentionMemberIds,
        String createdById,
        String createdByName,
        LocalDateTime createdAt
) {
}

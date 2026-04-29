package com.contentworkflow.document.interfaces.ws;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebSocket 评论回复载荷。
 */
@Data
public class DocumentWsCommentReply {
    private Long id;
    private Long commentId;
    private Long replyToReplyId;
    private String content;
    private List<String> mentionMemberIds;
    private String createdById;
    private String createdByName;
    private LocalDateTime createdAt;
}

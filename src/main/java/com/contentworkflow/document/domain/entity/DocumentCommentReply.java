package com.contentworkflow.document.domain.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档评论回复领域对象。
 */
@Data
@Builder
public class DocumentCommentReply {
    private Long id;
    private Long documentId;
    private Long commentId;
    private Long replyToReplyId;
    private String content;
    private List<String> mentionMemberIds;
    private String createdById;
    private String createdByName;
    private LocalDateTime createdAt;
}

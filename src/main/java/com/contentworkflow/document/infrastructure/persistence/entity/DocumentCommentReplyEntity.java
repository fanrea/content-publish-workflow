package com.contentworkflow.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * collaborative_document_comment_reply 表映射。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("collaborative_document_comment_reply")
public class DocumentCommentReplyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long commentId;
    private Long replyToReplyId;
    private String content;
    private String mentionMemberIds;
    private String createdById;
    private String createdByName;
    private LocalDateTime createdAt;

    /**
     * 统一补齐创建时间。
     */
    public void prepareForInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

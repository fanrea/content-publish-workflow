package com.contentworkflow.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * collaborative_document_member 表映射。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("collaborative_document_member")
public class DocumentMemberEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String memberId;
    private String memberName;
    private DocumentMemberRole memberRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 统一补齐创建/更新时间。
     */
    public void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}


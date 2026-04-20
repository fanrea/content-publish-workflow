package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("content_review_record")
public class ReviewRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long draftId;
    private Integer draftVersion;
    private String reviewer;
    private ReviewDecision decision;
    private String comment;
    private LocalDateTime reviewedAt;

    public void prepareForInsert() {
        if (reviewedAt == null) {
            reviewedAt = LocalDateTime.now();
        }
    }
}

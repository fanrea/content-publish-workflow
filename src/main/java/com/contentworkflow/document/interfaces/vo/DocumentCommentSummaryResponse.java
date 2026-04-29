package com.contentworkflow.document.interfaces.vo;

/**
 * 评论统计响应对象。
 */
public record DocumentCommentSummaryResponse(
        Long totalCount,
        Long unresolvedCount,
        Long resolvedCount
) {
}


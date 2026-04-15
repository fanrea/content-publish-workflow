package com.contentworkflow.workflow.interfaces.vo;

import java.util.List;

/**
 * 草稿统计结果（用于列表页顶部统计区/状态过滤器）。
 */
public record DraftStatsResponse(
        long total,
        List<DraftStatusCountResponse> byStatus
) {
}


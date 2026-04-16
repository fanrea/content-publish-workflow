package com.contentworkflow.workflow.interfaces.vo;

import java.util.List;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */
public record DraftStatsResponse(
        long total,
        List<DraftStatusCountResponse> byStatus
) {
}


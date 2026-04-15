package com.contentworkflow.workflow.interfaces.vo;

import java.time.LocalDateTime;

/**
 * 工作流摘要（详情页信息卡使用）。
 *
 * <p>这是轻量聚合信息：不返回快照/任务/日志的明细列表，仅返回计数与最新发布时间等。</p>
 */
public record DraftWorkflowSummaryResponse(
        ContentDraftSummaryResponse draft,
        long reviewCount,
        long snapshotCount,
        long publishTaskCount,
        long publishLogCount,
        Integer latestPublishedVersion,
        LocalDateTime latestPublishedAt
) {
}


package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;

/**
 * 草稿状态计数（用于列表页 Tab/Badge）。
 */
public record DraftStatusCountResponse(
        WorkflowStatus status,
        long count
) {
}


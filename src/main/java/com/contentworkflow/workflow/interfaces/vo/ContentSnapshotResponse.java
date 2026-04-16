package com.contentworkflow.workflow.interfaces.vo;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */

public record ContentSnapshotResponse(
        Long id,
        Integer publishedVersion,
        Integer sourceDraftVersion,
        String title,
        String summary,
        String body,
        String operator,
        boolean rollback,
        LocalDateTime publishedAt
) {
}

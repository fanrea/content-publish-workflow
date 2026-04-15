package com.contentworkflow.workflow.interfaces.vo;

import java.time.LocalDateTime;

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

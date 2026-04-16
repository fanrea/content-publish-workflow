package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.PublishChangeScope;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;

import java.util.List;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */
public record PublishDiffResponse(
        Long draftId,
        Integer draftVersion,
        Integer basePublishedVersion,
        Integer currentPublishedVersion,
        Integer nextPublishedVersion,
        boolean firstPublish,
        boolean hasChanges,
        List<FieldDiff> fields,
        List<ChangeScopeSummary> scopes,
        List<PlannedTask> plannedTasks
) {
    /**
     * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
     */

    public record FieldDiff(
            String field,
            boolean changed,
            String beforePreview,
            String afterPreview,
            String beforeSha256,
            String afterSha256
    ) {
    }

    /**
     * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
     */

    public record ChangeScopeSummary(
            PublishChangeScope scope,
            boolean changed,
            String reason
    ) {
    }

    /**
     * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
     */

    public record PlannedTask(
            PublishTaskType taskType,
            String reason
    ) {
    }
}

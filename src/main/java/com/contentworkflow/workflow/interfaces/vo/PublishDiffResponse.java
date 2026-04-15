package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.PublishChangeScope;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;

import java.util.List;

/**
 * Publish diff preview used by the confirmation page before a release.
 *
 * <p>The current version compares the three editable draft fields: title, summary, and body.</p>
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
    public record FieldDiff(
            String field,
            boolean changed,
            String beforePreview,
            String afterPreview,
            String beforeSha256,
            String afterSha256
    ) {
    }

    public record ChangeScopeSummary(
            PublishChangeScope scope,
            boolean changed,
            String reason
    ) {
    }

    public record PlannedTask(
            PublishTaskType taskType,
            String reason
    ) {
    }
}

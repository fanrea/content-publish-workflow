package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RollbackRequest(
        @NotNull @Min(1) Integer targetPublishedVersion,
        @Size(max = 500) String reason
) {
    /**
     * Backward-compatible constructor for older tests/callers that still pass operator first.
     */
    public RollbackRequest(String operator, Integer targetPublishedVersion, String reason) {
        this(targetPublishedVersion, reason);
    }
}

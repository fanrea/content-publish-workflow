package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.Size;

public record SubmitReviewRequest(
        @Size(max = 500) String remark
) {
    /**
     * Backward-compatible constructor for older tests/callers that still pass operator first.
     */
    public SubmitReviewRequest(String operator, String remark) {
        this(remark);
    }
}

package com.contentworkflow.workflow.interfaces.dto;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 500) String comment
) {
    /**
     * Backward-compatible constructor for older tests/callers that still pass reviewer first.
     */
    public ReviewDecisionRequest(String reviewer, ReviewDecision decision, String comment) {
        this(decision, comment);
    }
}

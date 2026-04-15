package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.Size;

/**
 * Manual recovery request for operational retry / requeue actions.
 */
public record ManualRecoveryRequest(
        @Size(max = 500) String remark
) {
}

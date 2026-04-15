package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDraftRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 500) String summary,
        @NotBlank String body
) {
}

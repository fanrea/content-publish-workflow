package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkflowLoginRequest(
        @NotBlank(message = "username must not be blank")
        String username,
        @NotBlank(message = "password must not be blank")
        String password
) {
}

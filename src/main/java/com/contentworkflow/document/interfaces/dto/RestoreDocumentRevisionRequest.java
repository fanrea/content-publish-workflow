package com.contentworkflow.document.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * RestoreDocumentRevisionRequest 请求对象，用于封装接口入参。
 */
public record RestoreDocumentRevisionRequest(
        @NotNull(message = "targetRevision must not be null")
        @Min(value = 1, message = "targetRevision must be >= 1")
        Integer targetRevision,
        @NotNull(message = "baseRevision must not be null")
        @Min(value = 1, message = "baseRevision must be >= 1")
        Integer baseRevision,
        String changeSummary
) {
}

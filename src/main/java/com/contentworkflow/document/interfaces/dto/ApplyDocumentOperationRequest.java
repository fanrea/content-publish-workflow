package com.contentworkflow.document.interfaces.dto;

import com.contentworkflow.document.domain.enums.DocumentOpType;
import jakarta.validation.constraints.NotNull;

public record ApplyDocumentOperationRequest(
        Integer expectedRevision,
        Integer baseRevision,
        @NotNull(message = "opType must not be null")
        DocumentOpType opType,
        Integer position,
        Integer length,
        String text,
        String title,
        String changeSummary
) {
}

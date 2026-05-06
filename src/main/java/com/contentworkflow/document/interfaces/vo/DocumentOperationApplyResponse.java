package com.contentworkflow.document.interfaces.vo;

public record DocumentOperationApplyResponse(
        boolean duplicated,
        CollaborativeDocumentResponse document,
        DocumentOperationResponse operation,
        DocumentRevisionResponse revision
) {
}

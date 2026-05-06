package com.contentworkflow.document.application.engine;

public record IngressRepairTask(
        Long docId,
        String sessionId,
        Long clientSeq,
        String payload,
        String failureReason,
        int retryCount,
        String status
) {
}

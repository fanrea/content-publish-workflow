package com.contentworkflow.workflow.domain.enums;

/**
 * Distinguishes mutually exclusive draft-level operations.
 */
public enum DraftOperationType {
    PUBLISH,
    ROLLBACK,
    OFFLINE
}

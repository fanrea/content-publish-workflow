package com.contentworkflow.workflow.domain.enums;

/**
 * Scope of a publish change.
 *
 * <p>Used to explain why a publish is needed and which side-effect tasks should run.</p>
 */
public enum PublishChangeScope {
    METADATA,
    BODY_CONTENT,
    BODY_STRUCTURE,
    FORMAT_ONLY
}

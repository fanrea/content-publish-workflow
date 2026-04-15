package com.contentworkflow.common.web.auth;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declare the workflow roles allowed to access an endpoint.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireWorkflowRole {

    WorkflowRole[] value();
}

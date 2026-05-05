package com.contentworkflow.document.application.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Validates delta store backend selection to avoid silently falling back on typo.
 */
@Component
public class DocumentDeltaStoreBackendValidator {

    private static final Set<String> SUPPORTED_BACKENDS = Set.of("mysql", "filesystem", "noop");

    public DocumentDeltaStoreBackendValidator(
            @Value("${workflow.operation-log.backend:}") String configuredBackend,
            @Value("${workflow.operation-log.mysql-compat-enabled:false}") boolean mysqlCompatEnabled) {
        String normalizedBackend = normalizeBackend(configuredBackend);
        if (!SUPPORTED_BACKENDS.contains(normalizedBackend)) {
            throw new IllegalStateException(
                    "unsupported workflow.operation-log.backend=" + configuredBackend
                            + ", supported values: mysql, filesystem, noop"
            );
        }
        if ("mysql".equals(normalizedBackend) && !mysqlCompatEnabled) {
            throw new IllegalStateException(
                    "workflow.operation-log.backend=mysql requires workflow.operation-log.mysql-compat-enabled=true"
            );
        }
    }

    private String normalizeBackend(String backend) {
        if (backend == null) {
            return "filesystem";
        }
        String normalized = backend.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "filesystem" : normalized;
    }
}

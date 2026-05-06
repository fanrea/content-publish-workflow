package com.contentworkflow.document.application.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Guards against local/dev storage adapters being used in production-ready mode.
 */
@Component
public class DocumentStorageProductionModeValidator {

    private static final Set<String> NON_PRODUCTION_BACKENDS = Set.of("filesystem", "noop");

    public DocumentStorageProductionModeValidator(
            @Value("${workflow.storage.production-mode:false}") boolean productionMode,
            @Value("${workflow.operation-log.backend:}") String operationLogBackend,
            @Value("${workflow.storage.snapshot.backend:}") String snapshotBackend) {
        if (!productionMode) {
            return;
        }
        String normalizedOperationLogBackend = normalizeBackend(operationLogBackend);
        String normalizedSnapshotBackend = normalizeBackend(snapshotBackend);

        List<String> violations = new ArrayList<>(2);
        if (NON_PRODUCTION_BACKENDS.contains(normalizedOperationLogBackend)) {
            violations.add("workflow.operation-log.backend=" + normalizedOperationLogBackend + " is local/dev only");
        }
        if (NON_PRODUCTION_BACKENDS.contains(normalizedSnapshotBackend)) {
            violations.add("workflow.storage.snapshot.backend=" + normalizedSnapshotBackend + " is local/dev only");
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "workflow.storage.production-mode=true requires production-ready storage backends; "
                            + String.join("; ", violations)
                            + ". Recommended baseline: MySQL metadata + shardable operation log backend "
                            + "(MySQL sharding or object-storage segments) + RocketMQ ordered ingest log + "
                            + "Redis recent ops + OSS/MinIO snapshot."
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

package com.contentworkflow.document.application.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnMissingBean(DocumentSnapshotStore.class)
public class NoopDocumentSnapshotStore implements DocumentSnapshotStore {

    @Override
    public void put(String snapshotRef, String content) {
        // Fallback implementation for environments where snapshot object storage is disabled.
    }

    @Override
    public Optional<String> get(String snapshotRef) {
        return Optional.empty();
    }
}

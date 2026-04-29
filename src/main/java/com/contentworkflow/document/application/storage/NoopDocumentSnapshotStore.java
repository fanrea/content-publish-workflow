package com.contentworkflow.document.application.storage;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NoopDocumentSnapshotStore implements DocumentSnapshotStore {

    @Override
    public void put(String snapshotRef, String content) {
        // Snapshot storage abstraction is ready. Noop implementation keeps runtime behavior unchanged.
    }

    @Override
    public Optional<String> get(String snapshotRef) {
        return Optional.empty();
    }
}

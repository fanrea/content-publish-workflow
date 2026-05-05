package com.contentworkflow.document.application.storage;

import java.util.Optional;

public interface DocumentSnapshotStore {

    /**
     * Stores a full snapshot payload using the provided logical snapshot reference.
     */
    void put(String snapshotRef, String content);

    /**
     * Loads a full snapshot payload by logical snapshot reference.
     */
    Optional<String> get(String snapshotRef);
}

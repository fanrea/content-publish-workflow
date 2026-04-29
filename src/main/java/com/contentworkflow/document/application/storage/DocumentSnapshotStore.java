package com.contentworkflow.document.application.storage;

import java.util.Optional;

public interface DocumentSnapshotStore {

    void put(String snapshotRef, String content);

    Optional<String> get(String snapshotRef);
}

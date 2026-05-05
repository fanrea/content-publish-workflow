package com.contentworkflow.document.application.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemDocumentSnapshotStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void putAndGet_shouldPersistSnapshotContent() {
        FileSystemDocumentSnapshotStore store = new FileSystemDocumentSnapshotStore(tempDir.toString());

        store.put("snapshot/101/1.bin", "hello snapshot");
        Optional<String> loaded = store.get("snapshot/101/1.bin");

        assertThat(loaded).contains("hello snapshot");
    }

    @Test
    void get_shouldReturnEmptyWhenSnapshotMissing() {
        FileSystemDocumentSnapshotStore store = new FileSystemDocumentSnapshotStore(tempDir.toString());

        Optional<String> loaded = store.get("snapshot/999/2.bin");

        assertThat(loaded).isEmpty();
    }
}

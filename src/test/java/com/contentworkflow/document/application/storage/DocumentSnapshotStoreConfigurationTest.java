package com.contentworkflow.document.application.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSnapshotStoreConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUseNoopSnapshotStoreByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(SnapshotStoreConfig.class)
                .run(context -> assertThat(context.getBean(DocumentSnapshotStore.class))
                        .isInstanceOf(NoopDocumentSnapshotStore.class));
    }

    @Test
    void shouldUseFilesystemSnapshotStoreWhenEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(SnapshotStoreConfig.class)
                .withPropertyValues(
                        "workflow.storage.snapshot.backend=filesystem",
                        "workflow.storage.snapshot.filesystem.root-dir=" + tempDir
                )
                .run(context -> assertThat(context.getBean(DocumentSnapshotStore.class))
                        .isInstanceOf(FileSystemDocumentSnapshotStore.class));
    }

    @Configuration
    @Import({
            NoopDocumentSnapshotStore.class,
            FileSystemDocumentSnapshotStore.class
    })
    static class SnapshotStoreConfig {
    }
}

package com.contentworkflow.document.application.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStorageProductionModeValidatorTest {

    @Test
    void shouldRejectFilesystemOperationLogBackendInProductionMode() {
        new ApplicationContextRunner()
                .withUserConfiguration(ValidationConfig.class)
                .withPropertyValues(
                        "workflow.storage.production-mode=true",
                        "workflow.operation-log.backend=filesystem",
                        "workflow.storage.snapshot.backend=oss"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("workflow.operation-log.backend=filesystem is local/dev only");
                });
    }

    @Test
    void shouldRejectNoopSnapshotBackendInProductionMode() {
        new ApplicationContextRunner()
                .withUserConfiguration(ValidationConfig.class)
                .withPropertyValues(
                        "workflow.storage.production-mode=true",
                        "workflow.operation-log.backend=mysql",
                        "workflow.storage.snapshot.backend=noop"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("workflow.storage.snapshot.backend=noop is local/dev only");
                });
    }

    @Test
    void shouldNotRejectDevDefaultsWhenProductionModeDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(ValidationConfig.class)
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Configuration
    @Import(DocumentStorageProductionModeValidator.class)
    static class ValidationConfig {
    }

    private String rootCauseMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null ? "" : current.getMessage();
    }
}

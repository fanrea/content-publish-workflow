package com.contentworkflow.document.application.storage;

import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentOperationMybatisMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocumentDeltaStoreConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUseNoopDeltaStoreByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(DeltaStoreConfig.class)
                .run(context -> assertThat(context.getBean(DocumentDeltaStore.class))
                        .isInstanceOf(NoopDocumentDeltaStore.class));
    }

    @Test
    void shouldUseMySqlDeltaStoreWhenEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(DeltaStoreConfig.class)
                .withPropertyValues("workflow.operation-log.backend=mysql")
                .run(context -> assertThat(context.getBean(DocumentDeltaStore.class))
                        .isInstanceOf(MySqlDocumentDeltaStore.class));
    }

    @Test
    void shouldUseFilesystemDeltaStoreWhenEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(DeltaStoreConfig.class)
                .withPropertyValues(
                        "workflow.operation-log.backend=filesystem",
                        "workflow.operation-log.filesystem.root-dir=" + tempDir
                )
                .run(context -> assertThat(context.getBean(DocumentDeltaStore.class))
                        .isInstanceOf(FileSystemDocumentDeltaStore.class));
    }

    @Test
    void shouldFailFastWhenBackendMisconfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(DeltaStoreConfig.class)
                .withPropertyValues("workflow.operation-log.backend=invalid-backend")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("unsupported workflow.operation-log.backend=invalid-backend");
                });
    }

    @Configuration
    @Import({
            DocumentDeltaStoreBackendValidator.class,
            NoopDocumentDeltaStore.class,
            MySqlDocumentDeltaStore.class,
            FileSystemDocumentDeltaStore.class
    })
    static class DeltaStoreConfig {

        @Bean
        DocumentOperationMybatisMapper documentOperationMybatisMapper() {
            return mock(DocumentOperationMybatisMapper.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}

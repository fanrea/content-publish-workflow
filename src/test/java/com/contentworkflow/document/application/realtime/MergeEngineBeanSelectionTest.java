package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.application.storage.DocumentDeltaStore;
import com.contentworkflow.document.application.storage.DocumentSnapshotStore;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentCommentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentRevisionMybatisMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MergeEngineBeanSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MergeEngineConfig.class);

    @Test
    void shouldUseOtMergeEngineByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MergeEngine.class);
            assertThat(context.getBean(MergeEngine.class)).isInstanceOf(OtMergeEngine.class);
            assertThat(context).hasSingleBean(OtMergeEngine.class);
            assertThat(context).doesNotHaveBean(CrdtMergeEngine.class);
            assertThat(context).doesNotHaveBean(CrdtPlaceholderMergeEngine.class);
            assertThat(context).hasSingleBean(DocumentOperationService.class);
        });
    }

    @Test
    void shouldUseOtMergeEngineWhenConfiguredAsOt() {
        contextRunner
                .withPropertyValues("workflow.realtime.merge-engine=ot")
                .run(context -> {
                    assertThat(context).hasSingleBean(MergeEngine.class);
                    assertThat(context.getBean(MergeEngine.class)).isInstanceOf(OtMergeEngine.class);
                    assertThat(context).hasSingleBean(OtMergeEngine.class);
                    assertThat(context).doesNotHaveBean(CrdtMergeEngine.class);
                    assertThat(context).doesNotHaveBean(CrdtPlaceholderMergeEngine.class);
                    assertThat(context).hasSingleBean(DocumentOperationService.class);
                });
    }

    @Test
    void shouldUseCrdtMergeEngineWhenConfiguredAsCrdt() {
        contextRunner
                .withPropertyValues("workflow.realtime.merge-engine=crdt")
                .run(context -> {
                    assertThat(context).hasSingleBean(MergeEngine.class);
                    assertThat(context.getBean(MergeEngine.class)).isInstanceOf(CrdtMergeEngine.class);
                    assertThat(context).doesNotHaveBean(OtMergeEngine.class);
                    assertThat(context).hasSingleBean(CrdtMergeEngine.class);
                    assertThat(context).doesNotHaveBean(CrdtPlaceholderMergeEngine.class);
                    assertThat(context).hasSingleBean(DocumentOperationService.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            DocumentOperationService.class,
            OtMergeEngine.class,
            CrdtMergeEngine.class
    })
    static class MergeEngineConfig {

        @Bean
        CollaborativeDocumentMybatisMapper collaborativeDocumentMybatisMapper() {
            return mock(CollaborativeDocumentMybatisMapper.class);
        }

        @Bean
        DocumentRevisionMybatisMapper documentRevisionMybatisMapper() {
            return mock(DocumentRevisionMybatisMapper.class);
        }

        @Bean
        DocumentDeltaStore documentDeltaStore() {
            return mock(DocumentDeltaStore.class);
        }

        @Bean
        DocumentSnapshotStore documentSnapshotStore() {
            return mock(DocumentSnapshotStore.class);
        }

        @Bean
        DocumentCommentMybatisMapper documentCommentMybatisMapper() {
            return mock(DocumentCommentMybatisMapper.class);
        }

        @Bean
        DocumentPermissionService documentPermissionService() {
            return mock(DocumentPermissionService.class);
        }

        @Bean
        DocumentCacheService documentCacheService() {
            return mock(DocumentCacheService.class);
        }

        @Bean
        DocumentEventPublisher documentEventPublisher() {
            return mock(DocumentEventPublisher.class);
        }
    }
}

package com.contentworkflow.document.application.gc;

import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentDomainEvent;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.application.realtime.crdt.CrdtSnapshotCodec;
import com.contentworkflow.document.application.storage.DocumentSnapshotStore;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotCompactionExecutorTest {

    private CollaborativeDocumentMybatisMapper documentMapper;
    private DocumentSnapshotStore snapshotStore;
    private DocumentCacheService cacheService;
    private DocumentEventPublisher eventPublisher;
    private SnapshotCompactionExecutor executor;

    @BeforeEach
    void setUp() {
        documentMapper = mock(CollaborativeDocumentMybatisMapper.class);
        snapshotStore = mock(DocumentSnapshotStore.class);
        cacheService = mock(DocumentCacheService.class);
        eventPublisher = mock(DocumentEventPublisher.class);
        executor = new SnapshotCompactionExecutor(documentMapper, snapshotStore, cacheService, eventPublisher);
    }

    @Test
    void execute_shouldPersistLatestSnapshotAndMetadataWhenDocumentExists() {
        CollaborativeDocumentEntity document = new CollaborativeDocumentEntity();
        document.setId(100L);
        document.setLatestRevision(12);
        document.setContent("hello");
        document.setUpdatedBy("alice");
        when(documentMapper.selectById(100L)).thenReturn(document);
        when(snapshotStore.get("snapshot/100/12.bin")).thenReturn(Optional.empty());

        DocumentCompactionTask task = new DocumentCompactionTask(
                100L,
                "UPDATE_COUNT",
                Instant.parse("2026-04-30T10:00:00Z")
        );
        executor.execute(task);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(snapshotStore, times(1)).put(eq("snapshot/100/12.bin"), payloadCaptor.capture());
        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        assertThat(codec.isEncodedPayload(payloadCaptor.getValue())).isTrue();
        assertThat(codec.decodeToText(payloadCaptor.getValue())).isEqualTo("hello");
        verify(documentMapper, times(1)).updateSnapshotMetadata(
                eq(100L),
                eq("snapshot/100/12.bin"),
                eq(12),
                eq("alice"),
                any()
        );
        verify(cacheService, times(1)).evict(100L);

        ArgumentCaptor<DocumentDomainEvent> eventCaptor = ArgumentCaptor.forClass(DocumentDomainEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("DOCUMENT_COMPACTION_COMPLETED");
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().revision()).isEqualTo(12);
        assertThat(eventCaptor.getValue().payload().get("snapshotRef")).isEqualTo("snapshot/100/12.bin");
        assertThat(eventCaptor.getValue().payload().get("sourceType")).isEqualTo("document_content");
        assertThat(eventCaptor.getValue().payload().get("snapshotReused")).isEqualTo(false);
        assertThat(eventCaptor.getValue().payload().get("metadataUpdated")).isEqualTo(true);
        assertThat(eventCaptor.getValue().payload().get("payloadSha256")).isNotNull();
    }

    @Test
    void execute_shouldSkipWhenDocumentDoesNotExist() {
        when(documentMapper.selectById(100L)).thenReturn(null);

        executor.execute(new DocumentCompactionTask(
                100L,
                "TIME_WINDOW",
                Instant.parse("2026-04-30T10:00:00Z")
        ));

        verify(snapshotStore, never()).put(any(), any());
        verify(documentMapper, never()).updateSnapshotMetadata(any(), any(), any(), any(), any());
        verify(cacheService, never()).evict(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void execute_shouldSkipInvalidTombstoneGcTaskWithoutUpperClock() {
        executor.execute(new DocumentCompactionTask(
                100L,
                "TOMBSTONE_GC",
                Instant.parse("2026-04-30T10:00:00Z"),
                null
        ));

        verify(documentMapper, never()).selectById(any());
        verify(snapshotStore, never()).put(any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void execute_shouldThrowWhenSnapshotWriteFailure() {
        CollaborativeDocumentEntity document = new CollaborativeDocumentEntity();
        document.setId(100L);
        document.setLatestRevision(12);
        document.setContent("hello");
        when(documentMapper.selectById(100L)).thenReturn(document);
        when(snapshotStore.get("snapshot/100/12.bin")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("disk unavailable"))
                .when(snapshotStore)
                .put(eq("snapshot/100/12.bin"), any());

        assertThatThrownBy(() -> executor.execute(new DocumentCompactionTask(
                100L,
                "GROWTH_RATIO",
                Instant.parse("2026-04-30T10:00:00Z")
        )))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("disk unavailable");

        verify(documentMapper, never()).updateSnapshotMetadata(any(), any(), any(), any(), any());
        verify(cacheService, never()).evict(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void execute_shouldThrowWhenMetadataUpdateFails() {
        CollaborativeDocumentEntity document = new CollaborativeDocumentEntity();
        document.setId(100L);
        document.setLatestRevision(12);
        document.setContent("hello");
        document.setUpdatedBy("alice");
        when(documentMapper.selectById(100L)).thenReturn(document);
        when(snapshotStore.get("snapshot/100/12.bin")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("metadata update failed"))
                .when(documentMapper)
                .updateSnapshotMetadata(eq(100L), eq("snapshot/100/12.bin"), eq(12), eq("alice"), any());

        assertThatThrownBy(() -> executor.execute(new DocumentCompactionTask(
                100L,
                "TIME_WINDOW",
                Instant.parse("2026-04-30T10:00:00Z")
        )))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("metadata update failed");

        verify(cacheService, never()).evict(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void execute_shouldUseExistingSnapshotAsBaselineWhenDocumentContentIsEmpty() {
        CollaborativeDocumentEntity document = new CollaborativeDocumentEntity();
        document.setId(100L);
        document.setLatestRevision(12);
        document.setContent("");
        document.setUpdatedBy("alice");
        document.setLatestSnapshotRef("snapshot/100/10.bin");
        when(documentMapper.selectById(100L)).thenReturn(document);
        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        when(snapshotStore.get("snapshot/100/10.bin")).thenReturn(Optional.of(codec.encodeText("hello from snapshot")));
        when(snapshotStore.get("snapshot/100/12.bin")).thenReturn(Optional.empty());

        executor.execute(new DocumentCompactionTask(
                100L,
                "TIME_WINDOW",
                Instant.parse("2026-04-30T10:00:00Z")
        ));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(snapshotStore).put(eq("snapshot/100/12.bin"), payloadCaptor.capture());
        assertThat(codec.decodeToText(payloadCaptor.getValue())).isEqualTo("hello from snapshot");

        ArgumentCaptor<DocumentDomainEvent> eventCaptor = ArgumentCaptor.forClass(DocumentDomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().get("sourceType")).isEqualTo("latest_snapshot_ref");
        assertThat(eventCaptor.getValue().payload().get("sourceSnapshotRef")).isEqualTo("snapshot/100/10.bin");
    }

    @Test
    void execute_shouldBeIdempotentWhenSnapshotAndMetadataAlreadyUpToDate() {
        CollaborativeDocumentEntity document = new CollaborativeDocumentEntity();
        document.setId(100L);
        document.setLatestRevision(12);
        document.setContent("hello");
        document.setUpdatedBy("alice");
        document.setLatestSnapshotRef("snapshot/100/12.bin");
        document.setLatestSnapshotRevision(12);
        when(documentMapper.selectById(100L)).thenReturn(document);
        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        when(snapshotStore.get("snapshot/100/12.bin")).thenReturn(Optional.of(codec.encodeText("hello")));

        executor.execute(new DocumentCompactionTask(
                100L,
                "TIME_WINDOW",
                Instant.parse("2026-04-30T10:00:00Z")
        ));

        verify(snapshotStore, never()).put(any(), any());
        verify(documentMapper, never()).updateSnapshotMetadata(any(), any(), any(), any(), any());
        verify(cacheService, never()).evict(any());

        ArgumentCaptor<DocumentDomainEvent> eventCaptor = ArgumentCaptor.forClass(DocumentDomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().get("snapshotReused")).isEqualTo(true);
        assertThat(eventCaptor.getValue().payload().get("metadataUpdated")).isEqualTo(false);
    }
}

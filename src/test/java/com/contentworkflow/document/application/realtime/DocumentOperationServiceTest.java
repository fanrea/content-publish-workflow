package com.contentworkflow.document.application.realtime;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.application.realtime.crdt.CrdtSnapshotCodec;
import com.contentworkflow.document.application.storage.DocumentDeltaStore;
import com.contentworkflow.document.application.storage.DocumentSnapshotStore;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentRevisionEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentCommentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentRevisionMybatisMapper;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentOperationServiceTest {

    @Mock
    private CollaborativeDocumentMybatisMapper documentMapper;
    @Mock
    private DocumentRevisionMybatisMapper revisionMapper;
    @Mock
    private DocumentDeltaStore deltaStore;
    @Mock
    private DocumentSnapshotStore snapshotStore;
    @Mock
    private DocumentCommentMybatisMapper commentMapper;
    @Mock
    private DocumentPermissionService permissionService;
    @Mock
    private DocumentCacheService cacheService;
    @Mock
    private DocumentEventPublisher eventPublisher;

    private DocumentOperationService service;
    private MergeEngine mergeEngine;

    @BeforeEach
    void setUp() {
        mergeEngine = new OtMergeEngine();
        service = newService(true);
    }

    private DocumentOperationService newService(boolean actorSingleWriterEnabled) {
        return new DocumentOperationService(
                documentMapper,
                revisionMapper,
                deltaStore,
                snapshotStore,
                commentMapper,
                permissionService,
                cacheService,
                eventPublisher,
                mergeEngine,
                actorSingleWriterEnabled
        );
    }

    @Test
    void newService_shouldRejectDisabledActorSingleWriterFlag() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> newService(false));
        assertThat(ex.getMessage()).contains("must stay enabled");
    }

    @Test
    void applyOperation_shouldRebasePositionWhenBaseRevisionIsStale() {
        CollaborativeDocumentEntity current = buildDocument(1L, 3L, "doc", "abc", 2);
        CollaborativeDocumentEntity saved = buildDocument(1L, 4L, "doc", "abXc", 3);
        DocumentOperationEntity appliedInsert = new DocumentOperationEntity();
        appliedInsert.setDocumentId(1L);
        appliedInsert.setRevisionNo(2);
        appliedInsert.setBaseRevision(1);
        appliedInsert.setOpType(DocumentOpType.INSERT);
        appliedInsert.setOpPosition(1);
        appliedInsert.setOpLength(0);
        appliedInsert.setOpText("b");
        appliedInsert.setEditorId("u2");
        appliedInsert.setClientSeq(10L);

        when(permissionService.requireCanEdit(1L, "u9")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-1", 11L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(deltaStore.listByRevisionRange(1L, 1, 1)).thenReturn(List.of(appliedInsert));
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(2), eq("doc"), eq("abXc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(101L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        DocumentOperationService.ApplyResult result = service.applyOperation(
                1L,
                1,
                "sess-1",
                11L,
                "u9",
                "Alice",
                buildInsert(1, "X")
        );

        assertThat(result.duplicated()).isFalse();
        assertThat(result.operation().getOpPosition()).isEqualTo(2);
        assertThat(result.document().getContent()).isEqualTo("abXc");
        verify(documentMapper).actorSingleWriterUpdate(eq(1L), eq(2), eq("doc"), eq("abXc"), eq(3), eq("Alice"), any());
    }

    @Test
    void applyOperation_shouldPersistOnlyOnceWhenSameSessionAndClientSeqIsRetried() {
        CollaborativeDocumentEntity current = buildDocument(1L, 5L, "doc", "abc", 2);
        CollaborativeDocumentEntity saved = buildDocument(1L, 6L, "doc", "aXbc", 3);
        AtomicReference<DocumentOperationEntity> processedRef = new AtomicReference<>();

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-1", 11L))
                .thenAnswer(invocation -> Optional.ofNullable(processedRef.get()));
        when(documentMapper.selectById(1L)).thenReturn(current, saved, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(2), eq("doc"), eq("aXbc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        doAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(99L);
            processedRef.set(inserted);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        }).when(deltaStore).appendIfAbsent(any(DocumentOperationEntity.class));

        DocumentOperationService.ApplyResult first = service.applyOperation(
                1L,
                2,
                "sess-1",
                11L,
                "u1",
                "Alice",
                buildInsert(1, "X")
        );

        DocumentOperationService.ApplyResult retried = service.applyOperation(
                1L,
                2,
                "sess-1",
                11L,
                "u1",
                "Alice",
                buildInsert(1, "X")
        );

        assertThat(first.duplicated()).isFalse();
        assertThat(retried.duplicated()).isTrue();
        assertThat(retried.operation().getId()).isEqualTo(first.operation().getId());
        assertThat(retried.document().getLatestRevision()).isEqualTo(3);

        verify(documentMapper, times(1)).actorSingleWriterUpdate(any(), any(), any(), any(), any(), any(), any());
        verify(revisionMapper, times(1)).insert(any(DocumentRevisionEntity.class));
        verify(deltaStore, times(1)).appendIfAbsent(any(DocumentOperationEntity.class));
    }

    @Test
    void applyOperation_shouldFailWithConflictWhenStaleBaseCannotBeSafelyRebased() {
        CollaborativeDocumentEntity current = buildDocument(1L, 5L, "doc", "abc", 4);
        DocumentOperationEntity onlyOneApplied = new DocumentOperationEntity();
        onlyOneApplied.setDocumentId(1L);
        onlyOneApplied.setRevisionNo(3);
        onlyOneApplied.setBaseRevision(2);
        onlyOneApplied.setOpType(DocumentOpType.INSERT);
        onlyOneApplied.setOpPosition(1);
        onlyOneApplied.setOpLength(0);
        onlyOneApplied.setOpText("Z");
        onlyOneApplied.setEditorId("u2");
        onlyOneApplied.setClientSeq(10L);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-1", 12L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current);
        when(deltaStore.listByRevisionRange(1L, 2, 2)).thenReturn(List.of(onlyOneApplied));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.applyOperation(
                1L,
                2,
                "sess-1",
                12L,
                "u1",
                "Alice",
                buildInsert(1, "X")
        ));

        assertThat(ex.getCode()).isEqualTo("DOCUMENT_CONCURRENT_MODIFICATION");
        verify(documentMapper, never()).actorSingleWriterUpdate(any(), any(), any(), any(), any(), any(), any());
        verify(revisionMapper, never()).insert(any(DocumentRevisionEntity.class));
        verify(deltaStore, never()).appendIfAbsent(any(DocumentOperationEntity.class));
    }

    @Test
    void applyOperation_shouldUseActorSingleWriterUpdateWithoutLockVersionGuard() {
        DocumentOperationService retryEnabledService = newService(true);

        CollaborativeDocumentEntity current = buildDocument(1L, 999L, "doc", "abc", 2);
        CollaborativeDocumentEntity saved = buildDocument(1L, 1000L, "doc", "abXc", 3);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-retry", 41L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(2), eq("doc"), eq("abXc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(109L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        DocumentOperationService.ApplyResult result = retryEnabledService.applyOperation(
                1L,
                2,
                "sess-retry",
                41L,
                "u1",
                "Alice",
                buildInsert(2, "X")
        );

        assertThat(result.duplicated()).isFalse();
        assertThat(result.operation().getOpPosition()).isEqualTo(2);
        assertThat(result.document().getLatestRevision()).isEqualTo(3);
        assertThat(result.document().getContent()).isEqualTo("abXc");
        verify(documentMapper, times(1)).actorSingleWriterUpdate(
                eq(1L),
                eq(2),
                eq("doc"),
                eq("abXc"),
                eq(3),
                eq("Alice"),
                any()
        );
        verify(deltaStore, times(1)).appendIfAbsent(any(DocumentOperationEntity.class));
    }

    @Test
    void applyOperation_shouldFailWithConflictWhenActorSingleWriterUpdateCannotCommit() {
        CollaborativeDocumentEntity current = buildDocument(1L, 10L, "doc", "abc", 5);
        CollaborativeDocumentEntity latest = buildDocument(1L, 11L, "doc", "aYbc", 6);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-conflict", 51L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, latest);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(5), eq("doc"), eq("abXc"), eq(6), eq("Alice"), any()))
                .thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.applyOperation(
                1L,
                5,
                "sess-conflict",
                51L,
                "u1",
                "Alice",
                buildInsert(2, "X")
        ));

        assertThat(ex.getCode()).isEqualTo("DOCUMENT_CONCURRENT_MODIFICATION");
        verify(documentMapper, times(1)).actorSingleWriterUpdate(any(), any(), any(), any(), any(), any(), any());
        verify(revisionMapper, never()).insert(any(DocumentRevisionEntity.class));
        verify(deltaStore, never()).appendIfAbsent(any(DocumentOperationEntity.class));
    }

    @Test
    void applyFullReplaceOperation_shouldBuildReplaceAgainstBaseRevisionSnapshot() {
        CollaborativeDocumentEntity current = buildDocument(1L, 5L, "old-title", "abc", 1);
        CollaborativeDocumentEntity saved = buildDocument(1L, 6L, "new-title", "xyz", 2);
        DocumentRevisionEntity baseSnapshot = new DocumentRevisionEntity();
        baseSnapshot.setDocumentId(1L);
        baseSnapshot.setRevisionNo(1);
        baseSnapshot.setTitle("old-title");
        baseSnapshot.setContent("abc");

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 1)).thenReturn(Optional.of(baseSnapshot));
        when(deltaStore.findBySessionSeq(1L, "http-sess-1", 1001L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(1), eq("new-title"), eq("xyz"), eq(2), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(102L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        DocumentOperationService.ApplyResult result = service.applyFullReplaceOperation(
                1L,
                1,
                "http-sess-1",
                1001L,
                "u1",
                "Alice",
                "new-title",
                "xyz",
                "replace-all"
        );

        assertThat(result.duplicated()).isFalse();
        assertThat(result.operation().getOpType()).isEqualTo(DocumentOpType.REPLACE);
        assertThat(result.operation().getOpPosition()).isEqualTo(0);
        assertThat(result.operation().getOpLength()).isEqualTo(3);
        assertThat(result.operation().getOpText()).isEqualTo("xyz");
        assertThat(result.document().getTitle()).isEqualTo("new-title");
        assertThat(result.document().getContent()).isEqualTo("xyz");
        assertThat(result.document().getLatestRevision()).isEqualTo(2);
        verify(revisionMapper).selectByDocumentIdAndRevisionNo(1L, 1);
    }

    @Test
    void applyFullReplaceOperation_shouldPersistRequestedRestoreChangeType() {
        CollaborativeDocumentEntity current = buildDocument(1L, 8L, "old-title", "abc", 3);
        CollaborativeDocumentEntity saved = buildDocument(1L, 9L, "restored-title", "xyz", 4);
        DocumentRevisionEntity baseSnapshot = new DocumentRevisionEntity();
        baseSnapshot.setDocumentId(1L);
        baseSnapshot.setRevisionNo(3);
        baseSnapshot.setTitle("old-title");
        baseSnapshot.setContent("abc");

        when(permissionService.requireCanEdit(1L, "owner-1")).thenReturn(DocumentMemberRole.OWNER);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 3)).thenReturn(Optional.of(baseSnapshot));
        when(deltaStore.findBySessionSeq(1L, "restore-sess", 2001L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(3), eq("restored-title"), eq("xyz"), eq(4), eq("Owner"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(103L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        service.applyFullReplaceOperation(
                1L,
                3,
                "restore-sess",
                2001L,
                "owner-1",
                "Owner",
                "restored-title",
                "xyz",
                "restore by owner",
                com.contentworkflow.document.domain.enums.DocumentChangeType.RESTORE
        );

        ArgumentCaptor<DocumentRevisionEntity> revisionCaptor = ArgumentCaptor.forClass(DocumentRevisionEntity.class);
        verify(revisionMapper).insert(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getChangeType())
                .isEqualTo(com.contentworkflow.document.domain.enums.DocumentChangeType.RESTORE);
        assertThat(revisionCaptor.getValue().getChangeSummary()).isEqualTo("restore by owner");
    }

    @Test
    void applyOperation_shouldStoreOnlyMetadataForNonSnapshotRevision() {
        CollaborativeDocumentEntity current = buildDocument(1L, 5L, "doc", "abc", 2);
        CollaborativeDocumentEntity saved = buildDocument(1L, 6L, "doc", "abXc", 3);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-1", 21L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(2), eq("doc"), eq("abXc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(104L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        service.applyOperation(
                1L,
                2,
                "sess-1",
                21L,
                "u1",
                "Alice",
                buildInsert(2, "X")
        );

        ArgumentCaptor<DocumentRevisionEntity> revisionCaptor = ArgumentCaptor.forClass(DocumentRevisionEntity.class);
        verify(revisionMapper).insert(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getRevisionNo()).isEqualTo(3);
        assertThat(revisionCaptor.getValue().getIsSnapshot()).isFalse();
        assertThat(revisionCaptor.getValue().getContent()).isNull();
        verify(snapshotStore, never()).put(any(), any());
    }

    @Test
    void applyOperation_shouldPersistSnapshotBodyToSnapshotStoreForSnapshotRevision() {
        CollaborativeDocumentEntity current = buildDocument(1L, 200L, "doc", "abc", 99);
        CollaborativeDocumentEntity saved = buildDocument(1L, 201L, "doc", "abXc", 100);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-snap", 22L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(99), eq("doc"), eq("abXc"), eq(100), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(904L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        service.applyOperation(
                1L,
                99,
                "sess-snap",
                22L,
                "u1",
                "Alice",
                buildInsert(2, "X")
        );

        ArgumentCaptor<DocumentRevisionEntity> revisionCaptor = ArgumentCaptor.forClass(DocumentRevisionEntity.class);
        verify(revisionMapper).insert(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getRevisionNo()).isEqualTo(100);
        assertThat(revisionCaptor.getValue().getIsSnapshot()).isTrue();
        assertThat(revisionCaptor.getValue().getContent()).isNull();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(snapshotStore).put(eq("snapshot/1/100.bin"), payloadCaptor.capture());
        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        assertThat(codec.decodeToText(payloadCaptor.getValue())).isEqualTo("abXc");
        verify(documentMapper).updateSnapshotMetadata(eq(1L), eq("snapshot/1/100.bin"), eq(100), eq("Alice"), any());
    }

    @Test
    void applyOperation_shouldPreferSnapshotReplayAsRuntimeTruthForNextApply() {
        CollaborativeDocumentEntity current = buildDocument(1L, 20L, "doc", "stale-content", 4);
        current.setLatestSnapshotRef("snapshot/1/2.bin");
        current.setLatestSnapshotRevision(2);
        CollaborativeDocumentEntity saved = buildDocument(1L, 21L, "doc", "abYd!", 5);

        DocumentRevisionEntity revision3 = new DocumentRevisionEntity();
        revision3.setDocumentId(1L);
        revision3.setRevisionNo(3);
        DocumentRevisionEntity revision4 = new DocumentRevisionEntity();
        revision4.setDocumentId(1L);
        revision4.setRevisionNo(4);

        DocumentOperationEntity op3 = new DocumentOperationEntity();
        op3.setDocumentId(1L);
        op3.setRevisionNo(3);
        op3.setOpType(DocumentOpType.INSERT);
        op3.setOpPosition(2);
        op3.setOpLength(0);
        op3.setOpText("Y");

        DocumentOperationEntity op4 = new DocumentOperationEntity();
        op4.setDocumentId(1L);
        op4.setRevisionNo(4);
        op4.setOpType(DocumentOpType.DELETE);
        op4.setOpPosition(3);
        op4.setOpLength(1);
        op4.setOpText(null);

        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-runtime", 221L)).thenReturn(Optional.empty());
        when(snapshotStore.get("snapshot/1/2.bin")).thenReturn(Optional.of(codec.encodeText("abXd")));
        when(revisionMapper.selectByRevisionRangeAsc(1L, 2, 4, 2)).thenReturn(List.of(revision3, revision4));
        when(deltaStore.findByRevision(1L, 3)).thenReturn(Optional.of(op3));
        when(deltaStore.findByRevision(1L, 4)).thenReturn(Optional.of(op4));
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(4), eq("doc"), eq("abYd!"), eq(5), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(2004L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        DocumentOperationService.ApplyResult result = service.applyOperation(
                1L,
                4,
                "sess-runtime",
                221L,
                "u1",
                "Alice",
                buildInsert(4, "!")
        );

        assertThat(result.duplicated()).isFalse();
        assertThat(result.document().getContent()).isEqualTo("abYd!");
        verify(snapshotStore).get("snapshot/1/2.bin");
        verify(revisionMapper).selectByRevisionRangeAsc(1L, 2, 4, 2);
        verify(deltaStore).findByRevision(1L, 3);
        verify(deltaStore).findByRevision(1L, 4);
    }

    @Test
    void applyFullReplaceOperation_shouldDecodeEncodedBaseRevisionContent() {
        CollaborativeDocumentEntity current = buildDocument(1L, 13L, "old-title", "abc", 3);
        CollaborativeDocumentEntity saved = buildDocument(1L, 14L, "new-title", "xyz", 4);

        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        DocumentRevisionEntity baseSnapshot = new DocumentRevisionEntity();
        baseSnapshot.setDocumentId(1L);
        baseSnapshot.setRevisionNo(3);
        baseSnapshot.setTitle("old-title");
        baseSnapshot.setContent(codec.encodeText("abc"));

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 3)).thenReturn(Optional.of(baseSnapshot));
        when(deltaStore.findBySessionSeq(1L, "http-sess-encoded", 901L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(3), eq("new-title"), eq("xyz"), eq(4), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(3004L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        DocumentOperationService.ApplyResult result = service.applyFullReplaceOperation(
                1L,
                3,
                "http-sess-encoded",
                901L,
                "u1",
                "Alice",
                "new-title",
                "xyz",
                "replace-encoded"
        );

        assertThat(result.operation().getOpType()).isEqualTo(DocumentOpType.REPLACE);
        assertThat(result.operation().getOpLength()).isEqualTo(3);
    }

    @Test
    void applyFullReplaceOperation_shouldReplayFromEncodedSnapshotStorePayload() {
        CollaborativeDocumentEntity current = buildDocument(1L, 7L, "title", "Xbc", 3);
        CollaborativeDocumentEntity saved = buildDocument(1L, 8L, "new-title", "done", 4);

        DocumentRevisionEntity baseRevision = new DocumentRevisionEntity();
        baseRevision.setDocumentId(1L);
        baseRevision.setRevisionNo(3);
        baseRevision.setTitle("title");
        baseRevision.setContent(null);
        baseRevision.setIsSnapshot(false);

        DocumentRevisionEntity snapshotRevision = new DocumentRevisionEntity();
        snapshotRevision.setDocumentId(1L);
        snapshotRevision.setRevisionNo(1);
        snapshotRevision.setContent(null);
        snapshotRevision.setIsSnapshot(true);

        DocumentRevisionEntity revision2 = new DocumentRevisionEntity();
        revision2.setDocumentId(1L);
        revision2.setRevisionNo(2);
        DocumentRevisionEntity revision3 = new DocumentRevisionEntity();
        revision3.setDocumentId(1L);
        revision3.setRevisionNo(3);

        DocumentOperationEntity op2 = new DocumentOperationEntity();
        op2.setDocumentId(1L);
        op2.setRevisionNo(2);
        op2.setOpType(DocumentOpType.INSERT);
        op2.setOpPosition(1);
        op2.setOpLength(0);
        op2.setOpText("X");

        DocumentOperationEntity op3 = new DocumentOperationEntity();
        op3.setDocumentId(1L);
        op3.setRevisionNo(3);
        op3.setOpType(DocumentOpType.DELETE);
        op3.setOpPosition(0);
        op3.setOpLength(1);
        op3.setOpText(null);

        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 3)).thenReturn(Optional.of(baseRevision));
        when(revisionMapper.selectLatestSnapshotByRevision(1L, 3)).thenReturn(Optional.of(snapshotRevision));
        when(snapshotStore.get("snapshot/1/1.bin")).thenReturn(Optional.of(codec.encodeText("abc")));
        when(revisionMapper.selectByRevisionRangeAsc(1L, 1, 3, 2)).thenReturn(List.of(revision2, revision3));
        when(deltaStore.findByRevision(1L, 2)).thenReturn(Optional.of(op2));
        when(deltaStore.findByRevision(1L, 3)).thenReturn(Optional.of(op3));
        when(deltaStore.findBySessionSeq(1L, "http-sess-encoded-snapshot", 3002L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(3), eq("new-title"), eq("done"), eq(4), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(1051L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        DocumentOperationService.ApplyResult result = service.applyFullReplaceOperation(
                1L,
                3,
                "http-sess-encoded-snapshot",
                3002L,
                "u1",
                "Alice",
                "new-title",
                "done",
                "replace after encoded snapshot replay"
        );

        assertThat(result.operation().getOpType()).isEqualTo(DocumentOpType.REPLACE);
        assertThat(result.operation().getOpLength()).isEqualTo(3);
        verify(snapshotStore).get("snapshot/1/1.bin");
        verify(deltaStore).findByRevision(1L, 2);
        verify(deltaStore).findByRevision(1L, 3);
    }

    @Test
    void applyFullReplaceOperation_shouldRebuildBaseRevisionContentWhenNotSnapshot() {
        CollaborativeDocumentEntity current = buildDocument(1L, 7L, "title", "Xbc", 3);
        CollaborativeDocumentEntity saved = buildDocument(1L, 8L, "new-title", "done", 4);

        DocumentRevisionEntity baseRevision = new DocumentRevisionEntity();
        baseRevision.setDocumentId(1L);
        baseRevision.setRevisionNo(3);
        baseRevision.setTitle("title");
        baseRevision.setContent(null);
        baseRevision.setIsSnapshot(false);

        DocumentRevisionEntity snapshotRevision = new DocumentRevisionEntity();
        snapshotRevision.setDocumentId(1L);
        snapshotRevision.setRevisionNo(1);
        snapshotRevision.setContent("abc");
        snapshotRevision.setIsSnapshot(true);

        DocumentRevisionEntity revision2 = new DocumentRevisionEntity();
        revision2.setDocumentId(1L);
        revision2.setRevisionNo(2);
        DocumentRevisionEntity revision3 = new DocumentRevisionEntity();
        revision3.setDocumentId(1L);
        revision3.setRevisionNo(3);

        DocumentOperationEntity op2 = new DocumentOperationEntity();
        op2.setDocumentId(1L);
        op2.setRevisionNo(2);
        op2.setOpType(DocumentOpType.INSERT);
        op2.setOpPosition(1);
        op2.setOpLength(0);
        op2.setOpText("X");

        DocumentOperationEntity op3 = new DocumentOperationEntity();
        op3.setDocumentId(1L);
        op3.setRevisionNo(3);
        op3.setOpType(DocumentOpType.DELETE);
        op3.setOpPosition(0);
        op3.setOpLength(1);
        op3.setOpText(null);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 3)).thenReturn(Optional.of(baseRevision));
        when(revisionMapper.selectLatestSnapshotByRevision(1L, 3)).thenReturn(Optional.of(snapshotRevision));
        when(revisionMapper.selectByRevisionRangeAsc(1L, 1, 3, 2)).thenReturn(List.of(revision2, revision3));
        when(deltaStore.findByRevision(1L, 2)).thenReturn(Optional.of(op2));
        when(deltaStore.findByRevision(1L, 3)).thenReturn(Optional.of(op3));
        when(deltaStore.findBySessionSeq(1L, "http-sess-2", 3001L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(3), eq("new-title"), eq("done"), eq(4), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(105L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        DocumentOperationService.ApplyResult result = service.applyFullReplaceOperation(
                1L,
                3,
                "http-sess-2",
                3001L,
                "u1",
                "Alice",
                "new-title",
                "done",
                "replace after replay"
        );

        assertThat(result.operation().getOpType()).isEqualTo(DocumentOpType.REPLACE);
        assertThat(result.operation().getOpLength()).isEqualTo(3);
        verify(revisionMapper).selectLatestSnapshotByRevision(1L, 3);
        verify(revisionMapper).selectByRevisionRangeAsc(1L, 1, 3, 2);
        verify(deltaStore).findByRevision(1L, 2);
        verify(deltaStore).findByRevision(1L, 3);
    }

    @Test
    void applyOperation_shouldUseBatchAnchorRelocationForInsert() {
        CollaborativeDocumentEntity current = buildDocument(1L, 10L, "doc", "abcdef", 4);
        CollaborativeDocumentEntity saved = buildDocument(1L, 11L, "doc", "abXYZcdef", 5);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-insert", 301L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(4), eq("doc"), eq("abXYZcdef"), eq(5), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(106L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        service.applyOperation(1L, 4, "sess-insert", 301L, "u1", "Alice", buildInsert(2, "XYZ"));

        verify(commentMapper).batchRelocateOpenAnchorsForInsert(1L, 2, 3);
        verifyNoMoreInteractions(commentMapper);
    }

    @Test
    void applyOperation_shouldUseBatchAnchorRelocationForDelete() {
        CollaborativeDocumentEntity current = buildDocument(1L, 12L, "doc", "abcdefghi", 5);
        CollaborativeDocumentEntity saved = buildDocument(1L, 13L, "doc", "abghi", 6);
        DocumentWsOperation deleteOp = new DocumentWsOperation();
        deleteOp.setOpType(DocumentOpType.DELETE);
        deleteOp.setPosition(2);
        deleteOp.setLength(4);
        deleteOp.setText(null);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-delete", 302L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(5), eq("doc"), eq("abghi"), eq(6), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(107L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        service.applyOperation(1L, 5, "sess-delete", 302L, "u1", "Alice", deleteOp);

        verify(commentMapper).batchRelocateOpenAnchorsForDelete(1L, 2, 4);
        verifyNoMoreInteractions(commentMapper);
    }

    @Test
    void applyOperation_shouldUseBatchAnchorRelocationForReplace() {
        CollaborativeDocumentEntity current = buildDocument(1L, 14L, "doc", "abcdefghi", 6);
        CollaborativeDocumentEntity saved = buildDocument(1L, 15L, "doc", "abZZghi", 7);
        DocumentWsOperation replaceOp = new DocumentWsOperation();
        replaceOp.setOpType(DocumentOpType.REPLACE);
        replaceOp.setPosition(2);
        replaceOp.setLength(4);
        replaceOp.setText("ZZ");

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "sess-replace", 303L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(6), eq("doc"), eq("abZZghi"), eq(7), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(108L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        service.applyOperation(1L, 6, "sess-replace", 303L, "u1", "Alice", replaceOp);

        verify(commentMapper).batchRelocateOpenAnchorsForReplace(1L, 2, 4, 2);
        verifyNoMoreInteractions(commentMapper);
    }

    @Test
    void applyOperation_shouldPreferDeltaBatchIdForIdempotencyLookup() {
        CollaborativeDocumentEntity current = buildDocument(1L, 5L, "doc", "abc", 2);
        DocumentOperationEntity processed = new DocumentOperationEntity();
        processed.setId(5001L);
        processed.setDocumentId(1L);
        processed.setRevisionNo(3);
        processed.setBaseRevision(2);
        processed.setSessionId("delta-batch:db-5001");
        processed.setClientSeq(99L);
        processed.setOpType(DocumentOpType.INSERT);
        processed.setOpPosition(1);
        processed.setOpLength(0);
        processed.setOpText("X");
        processed.setEditorId("u1");
        processed.setEditorName("Alice");

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "delta-batch:db-5001", 99L)).thenReturn(Optional.of(processed));
        when(documentMapper.selectById(1L)).thenReturn(current);

        DocumentOperationService.ApplyResult result = service.applyOperation(
                1L,
                2,
                "legacy-session",
                99L,
                "u1",
                "Alice",
                buildInsert(1, "X"),
                null,
                null,
                null,
                "db-5001",
                777L,
                Map.of("u1", 776L)
        );

        assertThat(result.duplicated()).isTrue();
        assertThat(result.operation().getId()).isEqualTo(5001L);
        verify(deltaStore).findBySessionSeq(1L, "delta-batch:db-5001", 99L);
        verify(deltaStore, never()).appendIfAbsent(any(DocumentOperationEntity.class));
    }

    @Test
    void applyOperation_shouldIncludeClientSubmitMetadataInEventPayload() {
        CollaborativeDocumentEntity current = buildDocument(1L, 5L, "doc", "abc", 2);
        CollaborativeDocumentEntity saved = buildDocument(1L, 6L, "doc", "aXbc", 3);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(deltaStore.findBySessionSeq(1L, "delta-batch:db-evt-1", 121L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.actorSingleWriterUpdate(eq(1L), eq(2), eq("doc"), eq("aXbc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(deltaStore.appendIfAbsent(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(201L);
            return new DocumentDeltaStore.AppendResult(false, inserted);
        });

        service.applyOperation(
                1L,
                2,
                "legacy-session",
                121L,
                "u1",
                "Alice",
                buildInsert(1, "X"),
                null,
                null,
                null,
                "db-evt-1",
                88L,
                Map.of("u1", 87L, "u2", 41L)
        );

        ArgumentCaptor<com.contentworkflow.document.application.event.DocumentDomainEvent> eventCaptor =
                ArgumentCaptor.forClass(com.contentworkflow.document.application.event.DocumentDomainEvent.class);
        verify(eventPublisher).publishAfterCommit(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().get("deltaBatchId")).isEqualTo("db-evt-1");
        assertThat(eventCaptor.getValue().payload().get("clientClock")).isEqualTo(88L);
        assertThat(eventCaptor.getValue().payload().get("baseVector")).isEqualTo(Map.of("u1", 87L, "u2", 41L));
    }

    private CollaborativeDocumentEntity buildDocument(Long id,
                                                      Long version,
                                                      String title,
                                                      String content,
                                                      Integer revision) {
        CollaborativeDocumentEntity entity = new CollaborativeDocumentEntity();
        entity.setId(id);
        entity.setVersion(version);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setLatestRevision(revision);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        return entity;
    }

    private DocumentWsOperation buildInsert(int position, String text) {
        DocumentWsOperation op = new DocumentWsOperation();
        op.setOpType(DocumentOpType.INSERT);
        op.setPosition(position);
        op.setLength(0);
        op.setText(text);
        return op;
    }
}


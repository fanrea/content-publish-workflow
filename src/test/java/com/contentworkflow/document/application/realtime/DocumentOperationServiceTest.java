package com.contentworkflow.document.application.realtime;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentRevisionEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentCommentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentOperationMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentRevisionMybatisMapper;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.util.List;
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
    private DocumentOperationMybatisMapper operationMapper;
    @Mock
    private DocumentCommentMybatisMapper commentMapper;
    @Mock
    private DocumentPermissionService permissionService;
    @Mock
    private DocumentCacheService cacheService;
    @Mock
    private DocumentEventPublisher eventPublisher;

    private DocumentOperationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentOperationService(
                documentMapper,
                revisionMapper,
                operationMapper,
                commentMapper,
                permissionService,
                cacheService,
                eventPublisher
        );
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
        when(operationMapper.selectBySessionSeq(1L, "sess-1", 11L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(operationMapper.selectByRevisionRange(1L, 1, 1)).thenReturn(List.of(appliedInsert));
        when(documentMapper.conditionalUpdate(eq(1L), eq(3L), eq(2), eq("doc"), eq("abXc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(operationMapper.insert(any(DocumentOperationEntity.class))).thenReturn(1);

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
        verify(documentMapper).conditionalUpdate(eq(1L), eq(3L), eq(2), eq("doc"), eq("abXc"), eq(3), eq("Alice"), any());
    }

    @Test
    void applyOperation_shouldPersistOnlyOnceWhenSameSessionAndClientSeqIsRetried() {
        CollaborativeDocumentEntity current = buildDocument(1L, 5L, "doc", "abc", 2);
        CollaborativeDocumentEntity saved = buildDocument(1L, 6L, "doc", "aXbc", 3);
        AtomicReference<DocumentOperationEntity> processedRef = new AtomicReference<>();

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(operationMapper.selectBySessionSeq(1L, "sess-1", 11L))
                .thenAnswer(invocation -> Optional.ofNullable(processedRef.get()));
        when(documentMapper.selectById(1L)).thenReturn(current, saved, saved);
        when(documentMapper.conditionalUpdate(eq(1L), eq(5L), eq(2), eq("doc"), eq("aXbc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        doAnswer(invocation -> {
            DocumentOperationEntity inserted = invocation.getArgument(0, DocumentOperationEntity.class);
            inserted.setId(99L);
            processedRef.set(inserted);
            return 1;
        }).when(operationMapper).insert(any(DocumentOperationEntity.class));

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

        verify(documentMapper, times(1)).conditionalUpdate(any(), any(), any(), any(), any(), any(), any(), any());
        verify(revisionMapper, times(1)).insert(any(DocumentRevisionEntity.class));
        verify(operationMapper, times(1)).insert(any(DocumentOperationEntity.class));
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
        when(operationMapper.selectBySessionSeq(1L, "sess-1", 12L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current);
        when(operationMapper.selectByRevisionRange(1L, 2, 2)).thenReturn(List.of(onlyOneApplied));

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
        verify(documentMapper, never()).conditionalUpdate(any(), any(), any(), any(), any(), any(), any(), any());
        verify(revisionMapper, never()).insert(any(DocumentRevisionEntity.class));
        verify(operationMapper, never()).insert(any(DocumentOperationEntity.class));
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
        when(operationMapper.selectBySessionSeq(1L, "http-sess-1", 1001L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.conditionalUpdate(eq(1L), eq(5L), eq(1), eq("new-title"), eq("xyz"), eq(2), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(operationMapper.insert(any(DocumentOperationEntity.class))).thenReturn(1);

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
        when(operationMapper.selectBySessionSeq(1L, "restore-sess", 2001L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.conditionalUpdate(eq(1L), eq(8L), eq(3), eq("restored-title"), eq("xyz"), eq(4), eq("Owner"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(operationMapper.insert(any(DocumentOperationEntity.class))).thenReturn(1);

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
        when(operationMapper.selectBySessionSeq(1L, "sess-1", 21L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.conditionalUpdate(eq(1L), eq(5L), eq(2), eq("doc"), eq("abXc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(operationMapper.insert(any(DocumentOperationEntity.class))).thenReturn(1);

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
        when(operationMapper.selectByRevision(1L, 2)).thenReturn(Optional.of(op2));
        when(operationMapper.selectByRevision(1L, 3)).thenReturn(Optional.of(op3));
        when(operationMapper.selectBySessionSeq(1L, "http-sess-2", 3001L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.conditionalUpdate(eq(1L), eq(7L), eq(3), eq("new-title"), eq("done"), eq(4), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(operationMapper.insert(any(DocumentOperationEntity.class))).thenReturn(1);

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
        verify(operationMapper).selectByRevision(1L, 2);
        verify(operationMapper).selectByRevision(1L, 3);
    }

    @Test
    void applyOperation_shouldUseBatchAnchorRelocationForInsert() {
        CollaborativeDocumentEntity current = buildDocument(1L, 10L, "doc", "abcdef", 4);
        CollaborativeDocumentEntity saved = buildDocument(1L, 11L, "doc", "abXYZcdef", 5);

        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(operationMapper.selectBySessionSeq(1L, "sess-insert", 301L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.conditionalUpdate(eq(1L), eq(10L), eq(4), eq("doc"), eq("abXYZcdef"), eq(5), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(operationMapper.insert(any(DocumentOperationEntity.class))).thenReturn(1);

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
        when(operationMapper.selectBySessionSeq(1L, "sess-delete", 302L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.conditionalUpdate(eq(1L), eq(12L), eq(5), eq("doc"), eq("abghi"), eq(6), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(operationMapper.insert(any(DocumentOperationEntity.class))).thenReturn(1);

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
        when(operationMapper.selectBySessionSeq(1L, "sess-replace", 303L)).thenReturn(Optional.empty());
        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(documentMapper.conditionalUpdate(eq(1L), eq(14L), eq(6), eq("doc"), eq("abZZghi"), eq(7), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);
        when(operationMapper.insert(any(DocumentOperationEntity.class))).thenReturn(1);

        service.applyOperation(1L, 6, "sess-replace", 303L, "u1", "Alice", replaceOp);

        verify(commentMapper).batchRelocateOpenAnchorsForReplace(1L, 2, 4, 2);
        verifyNoMoreInteractions(commentMapper);
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

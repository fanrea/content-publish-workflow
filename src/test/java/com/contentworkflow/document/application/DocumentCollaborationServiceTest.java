package com.contentworkflow.document.application;

import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.application.realtime.crdt.CrdtSnapshotCodec;
import com.contentworkflow.document.application.storage.DocumentDeltaStore;
import com.contentworkflow.document.application.storage.DocumentSnapshotStore;
import com.contentworkflow.document.domain.entity.DocumentRevision;
import com.contentworkflow.document.domain.enums.DocumentChangeType;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentRevisionEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentRevisionMybatisMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCollaborationServiceTest {

    @Mock
    private CollaborativeDocumentMybatisMapper documentMapper;
    @Mock
    private DocumentRevisionMybatisMapper revisionMapper;
    @Mock
    private DocumentDeltaStore deltaStore;
    @Mock
    private DocumentPermissionService permissionService;
    @Mock
    private DocumentCacheService cacheService;
    @Mock
    private DocumentEventPublisher eventPublisher;
    @Mock
    private DocumentSnapshotStore snapshotStore;

    private DocumentCollaborationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentCollaborationService(
                documentMapper,
                revisionMapper,
                deltaStore,
                permissionService,
                cacheService,
                eventPublisher,
                snapshotStore
        );
    }

    @Test
    void createDocument_shouldGenerateSnapshotRefAndPersistSnapshotMetadata() {
        when(documentMapper.insert(any(CollaborativeDocumentEntity.class))).thenAnswer(invocation -> {
            CollaborativeDocumentEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });

        service.createDocument("DOC-1", "Title", "hello", "u1", "Alice");

        ArgumentCaptor<String> snapshotRefCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> snapshotPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(snapshotStore).put(snapshotRefCaptor.capture(), snapshotPayloadCaptor.capture());

        String snapshotRef = snapshotRefCaptor.getValue();
        assertThat(snapshotRef).isEqualTo("snapshot/101/1.bin");
        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        assertThat(codec.decodeToText(snapshotPayloadCaptor.getValue())).isEqualTo("hello");
        verify(documentMapper).updateSnapshotMetadata(eq(101L), eq(snapshotRef), eq(1), eq("Alice"), any());

        ArgumentCaptor<DocumentRevisionEntity> revisionCaptor = ArgumentCaptor.forClass(DocumentRevisionEntity.class);
        verify(revisionMapper).insert(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getIsSnapshot()).isTrue();
        assertThat(revisionCaptor.getValue().getContent()).isNull();
    }

    @Test
    void getRevision_shouldReplayFromLatestSnapshotWhenTargetHasNoContent() {
        CollaborativeDocumentEntity document = buildDocument(1L, 4L, "title", "aXYd", 3);
        DocumentRevisionEntity target = new DocumentRevisionEntity();
        target.setDocumentId(1L);
        target.setRevisionNo(3);
        target.setTitle("title-3");
        target.setContent(null);
        target.setIsSnapshot(false);

        DocumentRevisionEntity snapshot = new DocumentRevisionEntity();
        snapshot.setDocumentId(1L);
        snapshot.setRevisionNo(1);
        snapshot.setContent("abc");
        snapshot.setIsSnapshot(true);

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
        op2.setOpPosition(3);
        op2.setOpLength(0);
        op2.setOpText("d");

        DocumentOperationEntity op3 = new DocumentOperationEntity();
        op3.setDocumentId(1L);
        op3.setRevisionNo(3);
        op3.setOpType(DocumentOpType.REPLACE);
        op3.setOpPosition(1);
        op3.setOpLength(2);
        op3.setOpText("XY");

        when(documentMapper.selectById(1L)).thenReturn(document);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 3)).thenReturn(Optional.of(target));
        when(revisionMapper.selectLatestSnapshotByRevision(1L, 3)).thenReturn(Optional.of(snapshot));
        when(revisionMapper.selectByRevisionRangeAsc(1L, 1, 3, 2)).thenReturn(List.of(revision2, revision3));
        when(deltaStore.findByRevision(1L, 2)).thenReturn(Optional.of(op2));
        when(deltaStore.findByRevision(1L, 3)).thenReturn(Optional.of(op3));

        DocumentRevision revision = service.getRevision(1L, 3);

        assertThat(revision.getTitle()).isEqualTo("title-3");
        assertThat(revision.getContent()).isEqualTo("aXYd");
    }

    @Test
    void editDocument_shouldStoreMetadataOnlyAndPersistSyntheticReplaceOperation() {
        CollaborativeDocumentEntity current = buildDocument(1L, 5L, "old-title", "abc", 2);
        CollaborativeDocumentEntity saved = buildDocument(1L, 6L, "new-title", "abZc", 3);

        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(documentMapper.conditionalUpdate(eq(1L), eq(5L), eq(2), eq("new-title"), eq("abZc"), eq(3), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);

        DocumentCollaborationService.DocumentEditResult result = service.editDocument(
                1L,
                2,
                "new-title",
                "abZc",
                "u1",
                "Alice",
                "summary",
                DocumentChangeType.EDIT
        );

        assertThat(result.document().getLatestRevision()).isEqualTo(3);

        ArgumentCaptor<DocumentRevisionEntity> revisionCaptor = ArgumentCaptor.forClass(DocumentRevisionEntity.class);
        verify(revisionMapper).insert(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getRevisionNo()).isEqualTo(3);
        assertThat(revisionCaptor.getValue().getIsSnapshot()).isFalse();
        assertThat(revisionCaptor.getValue().getContent()).isNull();

        ArgumentCaptor<DocumentOperationEntity> operationCaptor = ArgumentCaptor.forClass(DocumentOperationEntity.class);
        verify(deltaStore).appendIfAbsent(operationCaptor.capture());
        assertThat(operationCaptor.getValue().getRevisionNo()).isEqualTo(3);
        assertThat(operationCaptor.getValue().getBaseRevision()).isEqualTo(2);
        assertThat(operationCaptor.getValue().getOpType()).isEqualTo(DocumentOpType.REPLACE);
        assertThat(operationCaptor.getValue().getOpPosition()).isEqualTo(0);
        assertThat(operationCaptor.getValue().getOpLength()).isEqualTo(3);
        assertThat(operationCaptor.getValue().getOpText()).isEqualTo("abZc");
        verify(snapshotStore, never()).put(any(), any());
    }

    @Test
    void editDocument_shouldPersistSnapshotToObjectStoreWhenSnapshotRevision() {
        CollaborativeDocumentEntity current = buildDocument(1L, 9L, "old-title", "abc", 99);
        CollaborativeDocumentEntity saved = buildDocument(1L, 10L, "new-title", "abZc", 100);

        when(documentMapper.selectById(1L)).thenReturn(current, saved);
        when(permissionService.requireCanEdit(1L, "u1")).thenReturn(DocumentMemberRole.EDITOR);
        when(documentMapper.conditionalUpdate(eq(1L), eq(9L), eq(99), eq("new-title"), eq("abZc"), eq(100), eq("Alice"), any()))
                .thenReturn(1);
        when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenReturn(1);

        service.editDocument(
                1L,
                99,
                "new-title",
                "abZc",
                "u1",
                "Alice",
                "summary",
                DocumentChangeType.EDIT
        );

        ArgumentCaptor<String> snapshotPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(snapshotStore).put(eq("snapshot/1/100.bin"), snapshotPayloadCaptor.capture());
        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        assertThat(codec.decodeToText(snapshotPayloadCaptor.getValue())).isEqualTo("abZc");
        verify(documentMapper).updateSnapshotMetadata(eq(1L), eq("snapshot/1/100.bin"), eq(100), eq("Alice"), any());
    }

    @Test
    void getRevision_shouldLoadSnapshotFromObjectStoreWhenSnapshotContentMissingInMysql() {
        CollaborativeDocumentEntity document = buildDocument(1L, 4L, "title", "aXYd", 101);
        document.setLatestSnapshotRef("legacy/ref/101");
        DocumentRevisionEntity target = new DocumentRevisionEntity();
        target.setDocumentId(1L);
        target.setRevisionNo(101);
        target.setTitle("title-101");
        target.setContent(null);
        target.setIsSnapshot(true);

        DocumentRevisionEntity snapshot = new DocumentRevisionEntity();
        snapshot.setDocumentId(1L);
        snapshot.setRevisionNo(101);
        snapshot.setContent(null);
        snapshot.setIsSnapshot(true);

        when(documentMapper.selectById(1L)).thenReturn(document, document);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 101)).thenReturn(Optional.of(target));
        when(revisionMapper.selectLatestSnapshotByRevision(1L, 101)).thenReturn(Optional.of(snapshot));
        when(snapshotStore.get("snapshot/1/101.bin")).thenReturn(Optional.empty());
        when(snapshotStore.get("legacy/ref/101")).thenReturn(Optional.of("snapshot-body"));

        DocumentRevision revision = service.getRevision(1L, 101);

        assertThat(revision.getContent()).isEqualTo("snapshot-body");
    }

    @Test
    void getRevision_shouldKeepCompatibilityForPlainTextSnapshotPayload() {
        CollaborativeDocumentEntity document = buildDocument(1L, 4L, "title", "ignored", 101);
        DocumentRevisionEntity target = new DocumentRevisionEntity();
        target.setDocumentId(1L);
        target.setRevisionNo(101);
        target.setTitle("title-101");
        target.setContent(null);
        target.setIsSnapshot(true);

        DocumentRevisionEntity snapshot = new DocumentRevisionEntity();
        snapshot.setDocumentId(1L);
        snapshot.setRevisionNo(101);
        snapshot.setContent(null);
        snapshot.setIsSnapshot(true);

        when(documentMapper.selectById(1L)).thenReturn(document);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 101)).thenReturn(Optional.of(target));
        when(revisionMapper.selectLatestSnapshotByRevision(1L, 101)).thenReturn(Optional.of(snapshot));
        when(snapshotStore.get("snapshot/1/101.bin")).thenReturn(Optional.of("plain-snapshot-body"));

        DocumentRevision revision = service.getRevision(1L, 101);

        assertThat(revision.getContent()).isEqualTo("plain-snapshot-body");
    }

    @Test
    void getRevision_shouldDecodeCrdtEncodedSnapshotAndReplayOperations() {
        CollaborativeDocumentEntity document = buildDocument(1L, 4L, "title", "ignored", 102);
        DocumentRevisionEntity target = new DocumentRevisionEntity();
        target.setDocumentId(1L);
        target.setRevisionNo(102);
        target.setTitle("title-102");
        target.setContent(null);
        target.setIsSnapshot(false);

        DocumentRevisionEntity snapshot = new DocumentRevisionEntity();
        snapshot.setDocumentId(1L);
        snapshot.setRevisionNo(101);
        snapshot.setContent(null);
        snapshot.setIsSnapshot(true);

        DocumentRevisionEntity revision102 = new DocumentRevisionEntity();
        revision102.setDocumentId(1L);
        revision102.setRevisionNo(102);

        DocumentOperationEntity op102 = new DocumentOperationEntity();
        op102.setDocumentId(1L);
        op102.setRevisionNo(102);
        op102.setOpType(DocumentOpType.INSERT);
        op102.setOpPosition(3);
        op102.setOpLength(0);
        op102.setOpText("d");

        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        String encodedSnapshot = codec.encodeText("abc");

        when(documentMapper.selectById(1L)).thenReturn(document);
        when(revisionMapper.selectByDocumentIdAndRevisionNo(1L, 102)).thenReturn(Optional.of(target));
        when(revisionMapper.selectLatestSnapshotByRevision(1L, 102)).thenReturn(Optional.of(snapshot));
        when(revisionMapper.selectByRevisionRangeAsc(1L, 101, 102, 1)).thenReturn(List.of(revision102));
        when(deltaStore.findByRevision(1L, 102)).thenReturn(Optional.of(op102));
        when(snapshotStore.get("snapshot/1/101.bin")).thenReturn(Optional.of(encodedSnapshot));

        DocumentRevision revision = service.getRevision(1L, 102);

        assertThat(revision.getContent()).isEqualTo("abcd");
    }

    @Test
    void getDocument_shouldMaterializeLatestContentFromSnapshotAndOperationLogWhenTableContentIsStale() {
        CollaborativeDocumentEntity staleDocument = buildDocument(1L, 4L, "title", "stale-content", 3);
        staleDocument.setLatestSnapshotRef("snapshot/1/1.bin");
        staleDocument.setLatestSnapshotRevision(1);

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
        op2.setOpPosition(3);
        op2.setOpLength(0);
        op2.setOpText("d");

        DocumentOperationEntity op3 = new DocumentOperationEntity();
        op3.setDocumentId(1L);
        op3.setRevisionNo(3);
        op3.setOpType(DocumentOpType.REPLACE);
        op3.setOpPosition(1);
        op3.setOpLength(2);
        op3.setOpText("XY");

        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        when(cacheService.get(1L)).thenReturn(null);
        when(documentMapper.selectById(1L)).thenReturn(staleDocument);
        when(snapshotStore.get("snapshot/1/1.bin")).thenReturn(Optional.of(codec.encodeText("abc")));
        when(revisionMapper.selectByRevisionRangeAsc(1L, 1, 3, 2)).thenReturn(List.of(revision2, revision3));
        when(deltaStore.findByRevision(1L, 2)).thenReturn(Optional.of(op2));
        when(deltaStore.findByRevision(1L, 3)).thenReturn(Optional.of(op3));

        assertThat(service.getDocument(1L).getContent()).isEqualTo("aXYd");
    }

    @Test
    void getDocument_shouldFallbackToTableContentWhenReplayDataIsIncomplete() {
        CollaborativeDocumentEntity staleDocument = buildDocument(1L, 4L, "title", "stale-content", 3);
        staleDocument.setLatestSnapshotRef("snapshot/1/1.bin");
        staleDocument.setLatestSnapshotRevision(1);

        DocumentRevisionEntity revision2 = new DocumentRevisionEntity();
        revision2.setDocumentId(1L);
        revision2.setRevisionNo(2);

        CrdtSnapshotCodec codec = new CrdtSnapshotCodec();
        when(cacheService.get(1L)).thenReturn(null);
        when(documentMapper.selectById(1L)).thenReturn(staleDocument);
        when(snapshotStore.get("snapshot/1/1.bin")).thenReturn(Optional.of(codec.encodeText("abc")));
        when(revisionMapper.selectByRevisionRangeAsc(1L, 1, 3, 2)).thenReturn(List.of(revision2));

        assertThat(service.getDocument(1L).getContent()).isEqualTo("stale-content");
    }

    private CollaborativeDocumentEntity buildDocument(Long id,
                                                      Long version,
                                                      String title,
                                                      String content,
                                                      Integer revision) {
        CollaborativeDocumentEntity entity = new CollaborativeDocumentEntity();
        entity.setId(id);
        entity.setVersion(version);
        entity.setDocNo("DOC-1");
        entity.setTitle(title);
        entity.setContent(content);
        entity.setLatestRevision(revision);
        entity.setLatestSnapshotRef("snapshot/1/" + revision + ".bin");
        entity.setLatestSnapshotRevision(revision);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        return entity;
    }
}

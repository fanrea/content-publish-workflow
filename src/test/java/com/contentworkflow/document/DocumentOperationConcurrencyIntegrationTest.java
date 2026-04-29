package com.contentworkflow.document;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.application.realtime.DocumentOperationService;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentCommentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentRevisionEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentCommentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentOperationMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentRevisionMybatisMapper;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DocumentOperationConcurrencyIntegrationTest {

    @Test
    void concurrentEditingAndReplay_shouldConverge() throws Exception {
        InMemoryRealtimeHarness harness = new InMemoryRealtimeHarness();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<DocumentOperationService.ApplyResult> f1 = pool.submit(() -> {
                start.await(2, TimeUnit.SECONDS);
                return applyWithRetry(harness, "sess-u1", 1L, "u1", "Alice", insertAt(0, "A"));
            });
            Future<DocumentOperationService.ApplyResult> f2 = pool.submit(() -> {
                start.await(2, TimeUnit.SECONDS);
                return applyWithRetry(harness, "sess-u2", 1L, "u2", "Bob", insertAt(0, "B"));
            });
            start.countDown();
            DocumentOperationService.ApplyResult r1 = f1.get(5, TimeUnit.SECONDS);
            DocumentOperationService.ApplyResult r2 = f2.get(5, TimeUnit.SECONDS);

            assertThat(r1.duplicated()).isFalse();
            assertThat(r2.duplicated()).isFalse();
            assertThat(harness.state.snapshotDocument().getLatestRevision()).isEqualTo(3);
            assertThat(harness.state.snapshotDocument().getContent()).isEqualTo("AB");

            List<DocumentOperation> replay = harness.service.listOperationsSince(1L, 1, 20);
            assertThat(replay).hasSize(2);
            assertThat(replay).extracting(DocumentOperation::getRevisionNo).containsExactly(2, 3);
            assertThat(replay).extracting(DocumentOperation::getOpText).containsExactlyInAnyOrder("A", "B");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void idempotentRetry_shouldNotWriteTwice() {
        InMemoryRealtimeHarness harness = new InMemoryRealtimeHarness();

        DocumentOperationService.ApplyResult first = harness.service.applyOperation(
                1L,
                1,
                "sess-dup",
                100L,
                "u1",
                "Alice",
                insertAt(0, "X")
        );
        DocumentOperationService.ApplyResult retried = harness.service.applyOperation(
                1L,
                1,
                "sess-dup",
                100L,
                "u1",
                "Alice",
                insertAt(0, "X")
        );

        assertThat(first.duplicated()).isFalse();
        assertThat(retried.duplicated()).isTrue();
        assertThat(harness.state.snapshotDocument().getLatestRevision()).isEqualTo(2);
        assertThat(harness.state.countOperations()).isEqualTo(1);
    }

    private static DocumentWsOperation insertAt(int position, String text) {
        DocumentWsOperation op = new DocumentWsOperation();
        op.setOpType(DocumentOpType.INSERT);
        op.setPosition(position);
        op.setLength(0);
        op.setText(text);
        return op;
    }

    private static DocumentOperationService.ApplyResult applyWithRetry(InMemoryRealtimeHarness harness,
                                                                       String sessionId,
                                                                       Long clientSeq,
                                                                       String editorId,
                                                                       String editorName,
                                                                       DocumentWsOperation op) {
        int baseRevision = 1;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return harness.service.applyOperation(
                        1L,
                        baseRevision,
                        sessionId,
                        clientSeq,
                        editorId,
                        editorName,
                        op
                );
            } catch (BusinessException ex) {
                if (!"DOCUMENT_CONCURRENT_MODIFICATION".equals(ex.getCode())) {
                    throw ex;
                }
                baseRevision = harness.state.snapshotDocument().getLatestRevision();
            }
        }
        throw new IllegalStateException("apply operation retry exhausted");
    }

    private static final class InMemoryRealtimeHarness {
        private final InMemoryState state = new InMemoryState();
        private final DocumentOperationService service;

        private InMemoryRealtimeHarness() {
            CollaborativeDocumentMybatisMapper documentMapper = Mockito.mock(CollaborativeDocumentMybatisMapper.class);
            DocumentRevisionMybatisMapper revisionMapper = Mockito.mock(DocumentRevisionMybatisMapper.class);
            DocumentOperationMybatisMapper operationMapper = Mockito.mock(DocumentOperationMybatisMapper.class);
            DocumentCommentMybatisMapper commentMapper = Mockito.mock(DocumentCommentMybatisMapper.class);
            DocumentPermissionService permissionService = Mockito.mock(DocumentPermissionService.class);
            DocumentCacheService cacheService = Mockito.mock(DocumentCacheService.class);
            DocumentEventPublisher eventPublisher = Mockito.mock(DocumentEventPublisher.class);

            when(permissionService.requireCanEdit(eq(1L), anyString())).thenReturn(DocumentMemberRole.EDITOR);
            when(documentMapper.selectById(eq(1L))).thenAnswer(invocation -> state.snapshotDocument());
            when(documentMapper.conditionalUpdate(
                    eq(1L),
                    anyLong(),
                    anyInt(),
                    anyString(),
                    anyString(),
                    anyInt(),
                    anyString(),
                    any(LocalDateTime.class))
            ).thenAnswer(invocation -> state.conditionalUpdate(
                    invocation.getArgument(1, Long.class),
                    invocation.getArgument(2, Integer.class),
                    invocation.getArgument(3, String.class),
                    invocation.getArgument(4, String.class),
                    invocation.getArgument(5, Integer.class),
                    invocation.getArgument(6, String.class)
            ));
            when(operationMapper.selectBySessionSeq(eq(1L), anyString(), anyLong())).thenAnswer(invocation ->
                    state.selectBySessionSeq(
                            invocation.getArgument(1, String.class),
                            invocation.getArgument(2, Long.class)
                    ));
            when(operationMapper.selectByRevisionRange(eq(1L), anyInt(), anyInt())).thenAnswer(invocation ->
                    state.selectByRevisionRange(
                            invocation.getArgument(1, Integer.class),
                            invocation.getArgument(2, Integer.class)
                    ));
            when(operationMapper.insert(any(DocumentOperationEntity.class))).thenAnswer(invocation -> {
                state.insertOperation(invocation.getArgument(0, DocumentOperationEntity.class));
                return 1;
            });
            when(revisionMapper.insert(any(DocumentRevisionEntity.class))).thenAnswer(invocation -> {
                state.insertRevision(invocation.getArgument(0, DocumentRevisionEntity.class));
                return 1;
            });
            when(commentMapper.selectOpenByDocumentIdFromOffset(eq(1L), anyInt())).thenReturn(List.<DocumentCommentEntity>of());

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
    }

    private static final class InMemoryState {
        private final CollaborativeDocumentEntity document = new CollaborativeDocumentEntity();
        private final List<DocumentOperationEntity> operations = new ArrayList<>();
        private final List<DocumentRevisionEntity> revisions = new ArrayList<>();
        private long operationIdSeq = 1L;
        private long revisionIdSeq = 1L;

        private InMemoryState() {
            document.setId(1L);
            document.setVersion(1L);
            document.setDocNo("DOC-1");
            document.setTitle("doc");
            document.setContent("");
            document.setLatestRevision(1);
            document.setCreatedBy("system");
            document.setUpdatedBy("system");
        }

        private synchronized CollaborativeDocumentEntity snapshotDocument() {
            CollaborativeDocumentEntity copy = new CollaborativeDocumentEntity();
            copy.setId(document.getId());
            copy.setVersion(document.getVersion());
            copy.setDocNo(document.getDocNo());
            copy.setTitle(document.getTitle());
            copy.setContent(document.getContent());
            copy.setLatestRevision(document.getLatestRevision());
            copy.setCreatedBy(document.getCreatedBy());
            copy.setUpdatedBy(document.getUpdatedBy());
            copy.setCreatedAt(document.getCreatedAt());
            copy.setUpdatedAt(document.getUpdatedAt());
            return copy;
        }

        private synchronized int conditionalUpdate(Long expectedVersion,
                                                   Integer expectedBaseRevision,
                                                   String nextTitle,
                                                   String nextContent,
                                                   Integer nextRevision,
                                                   String updatedBy) {
            if (!document.getVersion().equals(expectedVersion)) {
                return 0;
            }
            if (!document.getLatestRevision().equals(expectedBaseRevision)) {
                return 0;
            }
            document.setVersion(document.getVersion() + 1);
            document.setTitle(nextTitle);
            document.setContent(nextContent);
            document.setLatestRevision(nextRevision);
            document.setUpdatedBy(updatedBy);
            document.setUpdatedAt(LocalDateTime.now());
            return 1;
        }

        private synchronized Optional<DocumentOperationEntity> selectBySessionSeq(String sessionId, Long clientSeq) {
            return operations.stream()
                    .filter(op -> op.getSessionId().equals(sessionId) && op.getClientSeq().equals(clientSeq))
                    .findFirst()
                    .map(this::copyOperation);
        }

        private synchronized List<DocumentOperationEntity> selectByRevisionRange(Integer fromRevisionExclusive, Integer limit) {
            return operations.stream()
                    .filter(op -> op.getRevisionNo() > fromRevisionExclusive)
                    .sorted(Comparator.comparing(DocumentOperationEntity::getRevisionNo))
                    .limit(limit)
                    .map(this::copyOperation)
                    .toList();
        }

        private synchronized void insertOperation(DocumentOperationEntity operation) {
            operation.setId(operationIdSeq++);
            operations.add(copyOperation(operation));
        }

        private synchronized void insertRevision(DocumentRevisionEntity revision) {
            revision.setId(revisionIdSeq++);
            revisions.add(copyRevision(revision));
        }

        private synchronized int countOperations() {
            return operations.size();
        }

        private DocumentOperationEntity copyOperation(DocumentOperationEntity source) {
            DocumentOperationEntity copy = new DocumentOperationEntity();
            copy.setId(source.getId());
            copy.setDocumentId(source.getDocumentId());
            copy.setRevisionNo(source.getRevisionNo());
            copy.setBaseRevision(source.getBaseRevision());
            copy.setSessionId(source.getSessionId());
            copy.setClientSeq(source.getClientSeq());
            copy.setOpType(source.getOpType());
            copy.setOpPosition(source.getOpPosition());
            copy.setOpLength(source.getOpLength());
            copy.setOpText(source.getOpText());
            copy.setEditorId(source.getEditorId());
            copy.setEditorName(source.getEditorName());
            copy.setCreatedAt(source.getCreatedAt());
            return copy;
        }

        private DocumentRevisionEntity copyRevision(DocumentRevisionEntity source) {
            DocumentRevisionEntity copy = new DocumentRevisionEntity();
            copy.setId(source.getId());
            copy.setDocumentId(source.getDocumentId());
            copy.setRevisionNo(source.getRevisionNo());
            copy.setBaseRevision(source.getBaseRevision());
            copy.setTitle(source.getTitle());
            copy.setContent(source.getContent());
            copy.setEditorId(source.getEditorId());
            copy.setEditorName(source.getEditorName());
            copy.setChangeType(source.getChangeType());
            copy.setChangeSummary(source.getChangeSummary());
            copy.setCreatedAt(source.getCreatedAt());
            return copy;
        }
    }
}

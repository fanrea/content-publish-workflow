package com.contentworkflow.document.application.realtime;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentDomainEvent;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.application.realtime.crdt.CrdtSnapshotCodec;
import com.contentworkflow.document.application.storage.DocumentDeltaStore;
import com.contentworkflow.document.application.storage.DocumentSnapshotStore;
import com.contentworkflow.document.domain.entity.CollaborativeDocument;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.entity.DocumentRevision;
import com.contentworkflow.document.domain.enums.DocumentChangeType;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentRevisionEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentCommentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentRevisionMybatisMapper;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class DocumentOperationService {

    private static final int SNAPSHOT_INTERVAL = resolveSnapshotInterval();
    private static final int MAX_REPLAY_LIMIT = 500;
    private static final int MAX_REBASE_OPS = 1000;
    private static final int MAX_SNAPSHOT_REPLAY_OPS = 2000;
    private static final int MAX_ACTOR_SINGLE_WRITER_RETRIES = 8;
    private static final int MAX_BASE_VECTOR_ENTRIES = 128;
    private static final int MAX_BASE_VECTOR_ACTOR_ID_LENGTH = 128;
    private static final int MAX_DELTA_BATCH_ID_LENGTH = 128;
    private static final String DELTA_BATCH_SESSION_PREFIX = "delta-batch:";
    private static final CrdtSnapshotCodec CRDT_SNAPSHOT_CODEC = new CrdtSnapshotCodec();

    private final CollaborativeDocumentMybatisMapper documentMapper;
    private final DocumentRevisionMybatisMapper revisionMapper;
    private final DocumentDeltaStore deltaStore;
    private final DocumentSnapshotStore snapshotStore;
    private final DocumentCommentMybatisMapper commentMapper;
    private final DocumentPermissionService permissionService;
    private final DocumentCacheService cacheService;
    private final DocumentEventPublisher eventPublisher;
    private final MergeEngine mergeEngine;
    private final boolean actorSingleWriterEnabled;

    public DocumentOperationService(CollaborativeDocumentMybatisMapper documentMapper,
                                    DocumentRevisionMybatisMapper revisionMapper,
                                    DocumentDeltaStore deltaStore,
                                    DocumentSnapshotStore snapshotStore,
                                    DocumentCommentMybatisMapper commentMapper,
                                    DocumentPermissionService permissionService,
                                    DocumentCacheService cacheService,
                                    DocumentEventPublisher eventPublisher,
                                    MergeEngine mergeEngine,
                                    @Value("${workflow.realtime.actor-single-writer.enabled:true}")
                                    boolean actorSingleWriterEnabled) {
        this.documentMapper = documentMapper;
        this.revisionMapper = revisionMapper;
        this.deltaStore = deltaStore;
        this.snapshotStore = snapshotStore;
        this.commentMapper = commentMapper;
        this.permissionService = permissionService;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
        this.mergeEngine = mergeEngine;
        this.actorSingleWriterEnabled = actorSingleWriterEnabled;
    }

    @Transactional
    public ApplyResult applyOperation(Long documentId,
                                      Integer baseRevision,
                                      String logicalSessionId,
                                      Long clientSeq,
                                      String editorId,
                                      String editorName,
                                      DocumentWsOperation op) {
        return applyOperation(
                documentId,
                baseRevision,
                logicalSessionId,
                clientSeq,
                editorId,
                editorName,
                op,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Transactional
    public ApplyResult applyOperation(Long documentId,
                                      Integer baseRevision,
                                      String logicalSessionId,
                                      Long clientSeq,
                                      String editorId,
                                      String editorName,
                                      DocumentWsOperation op,
                                      String requestedTitle,
                                      String requestedChangeSummary) {
        return applyOperation(
                documentId,
                baseRevision,
                logicalSessionId,
                clientSeq,
                editorId,
                editorName,
                op,
                requestedTitle,
                requestedChangeSummary,
                null,
                null,
                null,
                null
        );
    }

    @Transactional
    public ApplyResult applyOperation(Long documentId,
                                      Integer baseRevision,
                                      String logicalSessionId,
                                      Long clientSeq,
                                      String editorId,
                                      String editorName,
                                      DocumentWsOperation op,
                                      String requestedTitle,
                                      String requestedChangeSummary,
                                      DocumentChangeType requestedChangeType) {
        return applyOperation(
                documentId,
                baseRevision,
                logicalSessionId,
                clientSeq,
                editorId,
                editorName,
                op,
                requestedTitle,
                requestedChangeSummary,
                requestedChangeType,
                null,
                null,
                null
        );
    }

    @Transactional
    public ApplyResult applyOperation(Long documentId,
                                      Integer baseRevision,
                                      String logicalSessionId,
                                      Long clientSeq,
                                      String editorId,
                                      String editorName,
                                      DocumentWsOperation op,
                                      String requestedTitle,
                                      String requestedChangeSummary,
                                      DocumentChangeType requestedChangeType,
                                      String deltaBatchId,
                                      Long clientClock,
                                      Map<String, Long> baseVector) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        Integer normalizedBaseRevision = normalizeBaseRevision(baseRevision);
        String normalizedSessionId = normalizeLogicalSessionId(logicalSessionId);
        String normalizedDeltaBatchId = normalizeOptionalDeltaBatchId(deltaBatchId);
        Long normalizedClientClock = normalizeOptionalClientClock(clientClock);
        Map<String, Long> normalizedBaseVector = normalizeOptionalBaseVector(baseVector);
        String idempotencySessionId = resolveIdempotencySessionId(normalizedSessionId, normalizedDeltaBatchId);
        Long normalizedClientSeq = normalizeClientSeq(clientSeq);
        String normalizedEditorId = normalizeEditorId(editorId);
        String normalizedEditorName = normalizeEditorName(editorName);
        DocumentWsOperation normalizedOp = normalizeOperation(op);
        String normalizedRequestedTitle = normalizeOptionalTitle(requestedTitle);
        String normalizedRequestedChangeSummary = normalizeChangeSummary(requestedChangeSummary);
        DocumentChangeType resolvedChangeType = resolveChangeType(requestedChangeType);

        permissionService.requireCanEdit(normalizedDocId, normalizedEditorId);

        DocumentOperationEntity processed = deltaStore.findBySessionSeq(
                normalizedDocId,
                idempotencySessionId,
                normalizedClientSeq
        ).orElse(null);
        if (processed != null) {
            CollaborativeDocumentEntity current = requireDocument(normalizedDocId);
            cacheService.put(current);
            return new ApplyResult(
                    true,
                    toDocument(current),
                    toOperation(processed),
                    null
            );
        }

        int maxAttempts = actorSingleWriterEnabled ? MAX_ACTOR_SINGLE_WRITER_RETRIES + 1 : 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                DocumentOperationEntity duplicated = deltaStore.findBySessionSeq(
                        normalizedDocId,
                        idempotencySessionId,
                        normalizedClientSeq
                ).orElse(null);
                if (duplicated != null) {
                    CollaborativeDocumentEntity current = requireDocument(normalizedDocId);
                    cacheService.put(current);
                    return new ApplyResult(
                            true,
                            toDocument(current),
                            toOperation(duplicated),
                            null
                    );
                }
            }

            PreparedOperation prepared = prepareOperationAttempt(
                    normalizedDocId,
                    normalizedBaseRevision,
                    normalizedEditorId,
                    normalizedClientSeq,
                    normalizedOp,
                    normalizedRequestedTitle
            );

            LocalDateTime now = LocalDateTime.now();
            int updated = updateDocumentForOperation(
                    normalizedDocId,
                    prepared,
                    normalizedEditorName,
                    now
            );
            if (updated == 0) {
                if (actorSingleWriterEnabled && attempt < maxAttempts) {
                    continue;
                }
                CollaborativeDocumentEntity latest = requireDocument(normalizedDocId);
                throw concurrentModification(
                        normalizedDocId,
                        prepared.effectiveBaseRevision(),
                        latest.getLatestRevision()
                );
            }

            migrateOpenCommentAnchors(normalizedDocId, prepared.effectiveOp());

            DocumentRevisionEntity revision = new DocumentRevisionEntity();
            boolean snapshotRevision = shouldStoreSnapshotRevision(prepared.nextRevision());
            revision.setDocumentId(normalizedDocId);
            revision.setRevisionNo(prepared.nextRevision());
            revision.setBaseRevision(prepared.effectiveBaseRevision());
            revision.setTitle(prepared.nextTitle());
            // Snapshot body is persisted via snapshotStore; revision table keeps metadata catalog only.
            revision.setContent(null);
            revision.setIsSnapshot(snapshotRevision);
            revision.setEditorId(normalizedEditorId);
            revision.setEditorName(normalizedEditorName);
            revision.setChangeType(resolvedChangeType);
            revision.setChangeSummary(resolveChangeSummary(
                    normalizedRequestedChangeSummary,
                    prepared.effectiveOp(),
                    resolvedChangeType
            ));
            revision.setCreatedAt(now);
            revision.prepareForInsert();
            revisionMapper.insert(revision);
            if (snapshotRevision) {
                String snapshotRef = buildSnapshotRef(normalizedDocId, prepared.nextRevision());
                persistLatestSnapshot(
                        normalizedDocId,
                        prepared.nextRevision(),
                        snapshotRef,
                        prepared.nextContent(),
                        normalizedEditorName
                );
            }

            DocumentOperationEntity operation = new DocumentOperationEntity();
            operation.setDocumentId(normalizedDocId);
            operation.setRevisionNo(prepared.nextRevision());
            operation.setBaseRevision(prepared.effectiveBaseRevision());
            operation.setSessionId(idempotencySessionId);
            operation.setClientSeq(normalizedClientSeq);
            operation.setOpType(prepared.effectiveOp().getOpType());
            operation.setOpPosition(prepared.effectiveOp().getPosition());
            operation.setOpLength(prepared.effectiveOp().getLength() == null ? 0 : prepared.effectiveOp().getLength());
            operation.setOpText(prepared.effectiveOp().getText());
            operation.setEditorId(normalizedEditorId);
            operation.setEditorName(normalizedEditorName);
            operation.setCreatedAt(now);
            operation.prepareForInsert();

            DocumentDeltaStore.AppendResult appendResult = deltaStore.appendIfAbsent(operation);
            if (appendResult.duplicated()) {
                DocumentOperationEntity duplicated = appendResult.operation();
                CollaborativeDocumentEntity saved = requireDocument(normalizedDocId);
                cacheService.put(saved);
                return new ApplyResult(
                        true,
                        toDocument(saved),
                        toOperation(duplicated),
                        toRevision(revision)
                );
            }
            DocumentOperationEntity persistedOperation = appendResult.operation();

            CollaborativeDocumentEntity saved = requireDocument(normalizedDocId);
            cacheService.put(saved);
            eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                    resolveDomainEventType(resolvedChangeType),
                    normalizedDocId,
                    prepared.nextRevision(),
                    normalizedEditorId,
                    normalizedEditorName,
                    buildOperationPayload(
                            persistedOperation,
                            prepared.effectiveBaseRevision(),
                            normalizedClientSeq,
                            resolvedChangeType,
                            normalizedDeltaBatchId,
                            normalizedClientClock,
                            normalizedBaseVector
                    )
            ));
            return new ApplyResult(
                    false,
                    toDocument(saved),
                    toOperation(persistedOperation),
                    toRevision(revision)
            );
        }
        throw new IllegalStateException("unexpected applyOperation loop termination");
    }

    private int updateDocumentForOperation(Long documentId,
                                           PreparedOperation prepared,
                                           String normalizedEditorName,
                                           LocalDateTime now) {
        if (actorSingleWriterEnabled) {
            // Actor/queue serial order is the write authority; DB only gates durability with expected revision.
            return documentMapper.actorSingleWriterUpdate(
                    documentId,
                    prepared.effectiveBaseRevision(),
                    prepared.nextTitle(),
                    prepared.nextContent(),
                    prepared.nextRevision(),
                    normalizedEditorName,
                    now
            );
        }
        // Legacy compatibility path: optimistic lock still available behind the feature toggle.
        return documentMapper.conditionalUpdate(
                documentId,
                prepared.current().getVersion(),
                prepared.effectiveBaseRevision(),
                prepared.nextTitle(),
                prepared.nextContent(),
                prepared.nextRevision(),
                normalizedEditorName,
                now
        );
    }

    @Transactional
    public ApplyResult applyFullReplaceOperation(Long documentId,
                                                 Integer baseRevision,
                                                 String logicalSessionId,
                                                 Long clientSeq,
                                                 String editorId,
                                                 String editorName,
                                                 String title,
                                                 String content,
                                                 String changeSummary) {
        return applyFullReplaceOperation(
                documentId,
                baseRevision,
                logicalSessionId,
                clientSeq,
                editorId,
                editorName,
                title,
                content,
                changeSummary,
                null
        );
    }

    @Transactional
    public ApplyResult applyFullReplaceOperation(Long documentId,
                                                 Integer baseRevision,
                                                 String logicalSessionId,
                                                 Long clientSeq,
                                                 String editorId,
                                                 String editorName,
                                                 String title,
                                                 String content,
                                                 String changeSummary,
                                                 DocumentChangeType changeType) {
        return applyFullReplaceOperation(
                documentId,
                baseRevision,
                logicalSessionId,
                clientSeq,
                editorId,
                editorName,
                title,
                content,
                changeSummary,
                changeType,
                null,
                null,
                null
        );
    }

    @Transactional
    public ApplyResult applyFullReplaceOperation(Long documentId,
                                                 Integer baseRevision,
                                                 String logicalSessionId,
                                                 Long clientSeq,
                                                 String editorId,
                                                 String editorName,
                                                 String title,
                                                 String content,
                                                 String changeSummary,
                                                 DocumentChangeType changeType,
                                                 String deltaBatchId,
                                                 Long clientClock,
                                                 Map<String, Long> baseVector) {
        DocumentWsOperation replaceAll = buildFullReplaceOperation(documentId, baseRevision, content);
        return applyOperation(
                documentId,
                baseRevision,
                logicalSessionId,
                clientSeq,
                editorId,
                editorName,
                replaceAll,
                title,
                changeSummary,
                changeType,
                deltaBatchId,
                clientClock,
                baseVector
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentOperation> listOperationsSince(Long documentId,
                                                       Integer fromRevisionExclusive,
                                                       Integer limit) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        int normalizedFromRevision = normalizeFromRevision(fromRevisionExclusive);
        int normalizedLimit = normalizeReplayLimit(limit);
        requireDocument(normalizedDocId);
        return deltaStore.listByRevisionRange(normalizedDocId, normalizedFromRevision, normalizedLimit)
                .stream()
                .map(this::toOperation)
                .toList();
    }

    private PreparedOperation prepareOperationAttempt(Long documentId,
                                                      Integer baseRevision,
                                                      String editorId,
                                                      Long clientSeq,
                                                      DocumentWsOperation operation,
                                                      String requestedTitle) {
        CollaborativeDocumentEntity current = requireDocument(documentId);
        Integer currentRevision = current.getLatestRevision();
        if (baseRevision > currentRevision) {
            throw concurrentModification(documentId, baseRevision, currentRevision);
        }

        DocumentWsOperation effectiveOp = copyOperation(operation);
        int effectiveBaseRevision = baseRevision;
        if (!Objects.equals(currentRevision, baseRevision)) {
            int lag = currentRevision - baseRevision;
            if (lag > MAX_REBASE_OPS) {
                throw concurrentModification(documentId, baseRevision, currentRevision);
            }
            List<DocumentOperationEntity> appliedOps = deltaStore.listByRevisionRange(
                    documentId,
                    baseRevision,
                    lag
            );
            if (appliedOps.size() < lag) {
                throw concurrentModification(documentId, baseRevision, currentRevision);
            }
            effectiveOp = mergeEngine.rebase(
                    effectiveOp,
                    appliedOps,
                    editorId,
                    clientSeq
            );
            effectiveBaseRevision = currentRevision;
        }

        String currentContent = current.getContent() == null ? "" : current.getContent();
        String nextTitle = resolveNextTitle(current.getTitle(), requestedTitle);
        String nextContent = mergeEngine.apply(currentContent, effectiveOp);
        int nextRevision = effectiveBaseRevision + 1;
        return new PreparedOperation(
                current,
                effectiveOp,
                effectiveBaseRevision,
                nextTitle,
                nextContent,
                nextRevision
        );
    }

    private void migrateOpenCommentAnchors(Long documentId, DocumentWsOperation op) {
        int operationPosition = safeNonNegative(op.getPosition());
        int operationLength = safeNonNegative(op.getLength());
        int insertedLength = safeTextLength(op.getText());
        switch (op.getOpType()) {
            case INSERT -> commentMapper.batchRelocateOpenAnchorsForInsert(
                    documentId,
                    operationPosition,
                    insertedLength
            );
            case DELETE -> commentMapper.batchRelocateOpenAnchorsForDelete(
                    documentId,
                    operationPosition,
                    operationLength
            );
            case REPLACE -> commentMapper.batchRelocateOpenAnchorsForReplace(
                    documentId,
                    operationPosition,
                    operationLength,
                    insertedLength
            );
        }
    }

    private DocumentWsOperation copyOperation(DocumentWsOperation source) {
        DocumentWsOperation copy = new DocumentWsOperation();
        copy.setOpType(source.getOpType());
        copy.setPosition(source.getPosition());
        copy.setLength(source.getLength());
        copy.setText(source.getText());
        return copy;
    }

    private DocumentWsOperation normalizeOperation(DocumentWsOperation op) {
        if (op == null || op.getOpType() == null) {
            throw new BusinessException("INVALID_ARGUMENT", "operation type is required");
        }
        if (op.getPosition() == null) {
            throw new BusinessException("INVALID_ARGUMENT", "operation position is required");
        }
        if (op.getOpType() == DocumentOpType.DELETE
                && (op.getLength() == null || op.getLength() <= 0)) {
            throw new BusinessException("INVALID_ARGUMENT", "operation length must be > 0 for delete");
        }
        if (op.getOpType() == DocumentOpType.REPLACE
                && (op.getLength() == null || op.getLength() < 0)) {
            throw new BusinessException("INVALID_ARGUMENT", "operation length must be >= 0 for replace");
        }
        if (op.getOpType() == DocumentOpType.INSERT && op.getText() == null) {
            throw new BusinessException("INVALID_ARGUMENT", "insert text must not be null");
        }
        if (op.getOpType() == DocumentOpType.REPLACE && op.getText() == null) {
            throw new BusinessException("INVALID_ARGUMENT", "replace text must not be null");
        }
        if (op.getLength() == null) {
            op.setLength(0);
        }
        return op;
    }

    private String buildChangeSummary(DocumentWsOperation op) {
        return "op=" + op.getOpType()
                + ",position=" + op.getPosition()
                + ",length=" + (op.getLength() == null ? 0 : op.getLength());
    }

    private String resolveChangeSummary(String requestedChangeSummary,
                                        DocumentWsOperation op,
                                        DocumentChangeType changeType) {
        if (requestedChangeSummary != null) {
            return requestedChangeSummary;
        }
        if (changeType == DocumentChangeType.RESTORE) {
            return "restore by operation pipeline";
        }
        return buildChangeSummary(op);
    }

    private DocumentChangeType resolveChangeType(DocumentChangeType requestedChangeType) {
        return requestedChangeType == null ? DocumentChangeType.EDIT : requestedChangeType;
    }

    private String resolveDomainEventType(DocumentChangeType changeType) {
        if (changeType == DocumentChangeType.RESTORE) {
            return "DOCUMENT_RESTORED";
        }
        return "DOCUMENT_OPERATION_APPLIED";
    }

    private String resolveNextTitle(String currentTitle, String requestedTitle) {
        return requestedTitle == null ? currentTitle : requestedTitle;
    }

    private DocumentWsOperation buildFullReplaceOperation(Long documentId, Integer baseRevision, String content) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        Integer normalizedBaseRevision = normalizeBaseRevision(baseRevision);
        DocumentRevisionEntity baseSnapshot = revisionMapper.selectByDocumentIdAndRevisionNo(
                normalizedDocId,
                normalizedBaseRevision
        ).orElseThrow(() -> new BusinessException(
                "DOCUMENT_REVISION_NOT_FOUND",
                "base revision not found, documentId=" + normalizedDocId + ", baseRevision=" + normalizedBaseRevision
        ));
        String baseContent = materializeRevisionContent(normalizedDocId, baseSnapshot);

        DocumentWsOperation replaceAll = new DocumentWsOperation();
        replaceAll.setOpType(DocumentOpType.REPLACE);
        replaceAll.setPosition(0);
        replaceAll.setLength(baseContent.length());
        replaceAll.setText(content == null ? "" : content);
        return replaceAll;
    }

    private CollaborativeDocumentEntity requireDocument(Long documentId) {
        CollaborativeDocumentEntity entity = documentMapper.selectById(documentId);
        if (entity == null) {
            throw new BusinessException("DOCUMENT_NOT_FOUND", "document not found");
        }
        return entity;
    }

    private Long normalizeDocumentId(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "documentId must be > 0");
        }
        return documentId;
    }

    private Integer normalizeBaseRevision(Integer baseRevision) {
        if (baseRevision == null || baseRevision <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "baseRevision must be >= 1");
        }
        return baseRevision;
    }

    private int normalizeFromRevision(Integer fromRevisionExclusive) {
        if (fromRevisionExclusive == null || fromRevisionExclusive < 0) {
            throw new BusinessException("INVALID_ARGUMENT", "fromRevision must be >= 0");
        }
        return fromRevisionExclusive;
    }

    private int normalizeReplayLimit(Integer limit) {
        int resolved = limit == null ? 200 : limit;
        if (resolved <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "limit must be > 0");
        }
        return Math.min(resolved, MAX_REPLAY_LIMIT);
    }

    private String normalizeLogicalSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException("INVALID_ARGUMENT", "sessionId is required");
        }
        return sessionId.trim();
    }

    private String normalizeOptionalDeltaBatchId(String deltaBatchId) {
        if (deltaBatchId == null) {
            return null;
        }
        String normalized = deltaBatchId.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("INVALID_ARGUMENT", "deltaBatchId must not be blank");
        }
        if (normalized.length() > MAX_DELTA_BATCH_ID_LENGTH) {
            throw new BusinessException("INVALID_ARGUMENT", "deltaBatchId is too long");
        }
        return normalized;
    }

    private Long normalizeOptionalClientClock(Long clientClock) {
        if (clientClock == null) {
            return null;
        }
        if (clientClock <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "clientClock must be > 0");
        }
        return clientClock;
    }

    private Map<String, Long> normalizeOptionalBaseVector(Map<String, Long> baseVector) {
        if (baseVector == null || baseVector.isEmpty()) {
            return baseVector;
        }
        if (baseVector.size() > MAX_BASE_VECTOR_ENTRIES) {
            throw new BusinessException("INVALID_ARGUMENT", "baseVector size exceeds limit");
        }
        for (Map.Entry<String, Long> entry : baseVector.entrySet()) {
            String actorId = entry.getKey();
            Long clock = entry.getValue();
            if (actorId == null || actorId.isBlank()) {
                throw new BusinessException("INVALID_ARGUMENT", "baseVector actor id must not be blank");
            }
            if (actorId.length() > MAX_BASE_VECTOR_ACTOR_ID_LENGTH) {
                throw new BusinessException("INVALID_ARGUMENT", "baseVector actor id is too long");
            }
            if (clock == null || clock < 0) {
                throw new BusinessException("INVALID_ARGUMENT", "baseVector clock must be >= 0");
            }
        }
        return baseVector;
    }

    private String resolveIdempotencySessionId(String logicalSessionId, String deltaBatchId) {
        if (deltaBatchId != null) {
            return DELTA_BATCH_SESSION_PREFIX + deltaBatchId;
        }
        return logicalSessionId;
    }

    private Long normalizeClientSeq(Long clientSeq) {
        if (clientSeq == null || clientSeq <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "clientSeq must be > 0");
        }
        return clientSeq;
    }

    private String normalizeEditorId(String editorId) {
        if (editorId == null || editorId.isBlank()) {
            return "anonymous";
        }
        return editorId.trim();
    }

    private String normalizeEditorName(String editorName) {
        if (editorName == null || editorName.isBlank()) {
            return "anonymous";
        }
        return editorName.trim();
    }

    private String normalizeOptionalTitle(String title) {
        if (title == null) {
            return null;
        }
        String normalized = title.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("INVALID_ARGUMENT", "title must not be blank");
        }
        return normalized;
    }

    private String normalizeChangeSummary(String changeSummary) {
        if (changeSummary == null) {
            return null;
        }
        String normalized = changeSummary.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int safeNonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private int safeTextLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private boolean shouldStoreSnapshotRevision(int revisionNo) {
        return revisionNo == 1 || revisionNo % SNAPSHOT_INTERVAL == 0;
    }

    private String materializeRevisionContent(Long documentId, DocumentRevisionEntity targetRevision) {
        if (targetRevision.getContent() != null) {
            return targetRevision.getContent();
        }
        int targetRevisionNo = targetRevision.getRevisionNo();
        DocumentRevisionEntity snapshot = revisionMapper.selectLatestSnapshotByRevision(documentId, targetRevisionNo)
                .orElseThrow(() -> new BusinessException(
                        "DOCUMENT_SNAPSHOT_NOT_FOUND",
                        "snapshot not found, documentId=" + documentId + ", revisionNo=" + targetRevisionNo
                ));
        String rebuilt = loadSnapshotContent(documentId, snapshot);
        int replayCount = targetRevisionNo - snapshot.getRevisionNo();
        if (replayCount <= 0) {
            return rebuilt;
        }
        if (replayCount > MAX_SNAPSHOT_REPLAY_OPS) {
            throw new BusinessException(
                    "DOCUMENT_REPLAY_LIMIT_EXCEEDED",
                    "replay exceeds limit, documentId=" + documentId
                            + ", fromRevision=" + snapshot.getRevisionNo()
                            + ", targetRevision=" + targetRevisionNo
            );
        }
        List<DocumentRevisionEntity> revisionsToReplay = revisionMapper.selectByRevisionRangeAsc(
                documentId,
                snapshot.getRevisionNo(),
                targetRevisionNo,
                replayCount
        );
        if (revisionsToReplay.size() < replayCount) {
            throw new BusinessException(
                    "DOCUMENT_REVISION_NOT_FOUND",
                    "replay revisions not complete, documentId=" + documentId
                            + ", fromRevision=" + snapshot.getRevisionNo()
                            + ", targetRevision=" + targetRevisionNo
            );
        }
        for (DocumentRevisionEntity replayRevision : revisionsToReplay) {
            DocumentOperationEntity operation = deltaStore.findByRevision(documentId, replayRevision.getRevisionNo())
                    .orElseThrow(() -> new BusinessException(
                            "DOCUMENT_OPERATION_NOT_FOUND",
                            "replay operation missing, documentId=" + documentId
                                    + ", revisionNo=" + replayRevision.getRevisionNo()
                    ));
            rebuilt = mergeEngine.apply(rebuilt, toWsOperation(operation));
        }
        return rebuilt;
    }

    private String buildSnapshotRef(Long documentId, Integer revisionNo) {
        if (documentId == null || revisionNo == null) {
            throw new BusinessException("INVALID_ARGUMENT", "snapshot reference requires documentId and revisionNo");
        }
        return "snapshot/" + documentId + "/" + revisionNo + ".bin";
    }

    private void persistLatestSnapshot(Long documentId,
                                       Integer revisionNo,
                                       String snapshotRef,
                                       String content,
                                       String updatedBy) {
        snapshotStore.put(snapshotRef, CRDT_SNAPSHOT_CODEC.encodeText(content == null ? "" : content));
        documentMapper.updateSnapshotMetadata(
                documentId,
                snapshotRef,
                revisionNo,
                updatedBy,
                LocalDateTime.now()
        );
    }

    private String loadSnapshotContent(Long documentId, DocumentRevisionEntity snapshotRevision) {
        if (snapshotRevision.getContent() != null) {
            return snapshotRevision.getContent();
        }

        String defaultSnapshotRef = buildSnapshotRef(documentId, snapshotRevision.getRevisionNo());
        Optional<String> loaded = snapshotStore.get(defaultSnapshotRef);
        if (loaded.isPresent()) {
            return decodeSnapshotPayload(loaded.get());
        }

        CollaborativeDocumentEntity document = documentMapper.selectById(documentId);
        if (document != null
                && Objects.equals(document.getLatestSnapshotRevision(), snapshotRevision.getRevisionNo())
                && document.getLatestSnapshotRef() != null
                && !document.getLatestSnapshotRef().isBlank()) {
            return snapshotStore.get(document.getLatestSnapshotRef())
                    .map(this::decodeSnapshotPayload)
                    .orElseThrow(() -> new BusinessException(
                            "DOCUMENT_SNAPSHOT_NOT_FOUND",
                            "snapshot object missing, documentId=" + documentId + ", revisionNo=" + snapshotRevision.getRevisionNo()
                    ));
        }

        throw new BusinessException(
                "DOCUMENT_SNAPSHOT_NOT_FOUND",
                "snapshot object missing, documentId=" + documentId + ", revisionNo=" + snapshotRevision.getRevisionNo()
        );
    }

    private String decodeSnapshotPayload(String payload) {
        if (payload == null) {
            return "";
        }
        if (!CRDT_SNAPSHOT_CODEC.isEncodedPayload(payload)) {
            return payload;
        }
        return CRDT_SNAPSHOT_CODEC.decodeToText(payload);
    }

    private DocumentWsOperation toWsOperation(DocumentOperationEntity operation) {
        DocumentWsOperation op = new DocumentWsOperation();
        op.setOpType(operation.getOpType());
        op.setPosition(operation.getOpPosition());
        op.setLength(operation.getOpLength());
        op.setText(operation.getOpText());
        return op;
    }

    private static int resolveSnapshotInterval() {
        Integer configured = Integer.getInteger("document.snapshot.interval");
        if (configured == null || configured <= 0) {
            return 100;
        }
        return configured;
    }

    private Map<String, Object> buildOperationPayload(DocumentOperationEntity operation,
                                                      Integer baseRevision,
                                                      Long clientSeq,
                                                      DocumentChangeType changeType,
                                                      String deltaBatchId,
                                                      Long clientClock,
                                                      Map<String, Long> baseVector) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("operationId", operation.getId());
        payload.put("baseRevision", baseRevision);
        payload.put("clientSeq", clientSeq);
        payload.put("deltaBatchId", deltaBatchId);
        payload.put("clientClock", clientClock);
        payload.put("baseVector", baseVector);
        payload.put("changeType", changeType == null ? null : changeType.name());
        payload.put("opType", operation.getOpType() == null ? null : operation.getOpType().name());
        payload.put("opPosition", operation.getOpPosition());
        payload.put("opLength", operation.getOpLength());
        return payload;
    }

    private CollaborativeDocument toDocument(CollaborativeDocumentEntity entity) {
        return CollaborativeDocument.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .docNo(entity.getDocNo())
                .title(entity.getTitle())
                .content(entity.getContent())
                .latestRevision(entity.getLatestRevision())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private DocumentOperation toOperation(DocumentOperationEntity entity) {
        return DocumentOperation.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .revisionNo(entity.getRevisionNo())
                .baseRevision(entity.getBaseRevision())
                .sessionId(entity.getSessionId())
                .clientSeq(entity.getClientSeq())
                .opType(entity.getOpType())
                .opPosition(entity.getOpPosition())
                .opLength(entity.getOpLength())
                .opText(entity.getOpText())
                .editorId(entity.getEditorId())
                .editorName(entity.getEditorName())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private DocumentRevision toRevision(DocumentRevisionEntity entity) {
        return DocumentRevision.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .revisionNo(entity.getRevisionNo())
                .baseRevision(entity.getBaseRevision())
                .title(entity.getTitle())
                .content(entity.getContent())
                .editorId(entity.getEditorId())
                .editorName(entity.getEditorName())
                .changeType(entity.getChangeType())
                .changeSummary(entity.getChangeSummary())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private BusinessException concurrentModification(Long documentId, Integer baseRevision, Integer latestRevision) {
        return new BusinessException(
                "DOCUMENT_CONCURRENT_MODIFICATION",
                "stale baseRevision, documentId=" + documentId
                        + ", baseRevision=" + baseRevision
                        + ", latestRevision=" + latestRevision
        );
    }

    private record PreparedOperation(
            CollaborativeDocumentEntity current,
            DocumentWsOperation effectiveOp,
            int effectiveBaseRevision,
            String nextTitle,
            String nextContent,
            int nextRevision
    ) {
    }

    public record ApplyResult(
            boolean duplicated,
            CollaborativeDocument document,
            DocumentOperation operation,
            DocumentRevision revision
    ) {
    }
}


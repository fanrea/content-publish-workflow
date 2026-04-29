package com.contentworkflow.document.application.realtime;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentDomainEvent;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
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
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentOperationMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentRevisionMybatisMapper;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
@Service
public class DocumentOperationService {

    private static final int SNAPSHOT_INTERVAL = resolveSnapshotInterval();
    private static final int MAX_REPLAY_LIMIT = 500;
    private static final int MAX_REBASE_OPS = 1000;
    private static final int MAX_SNAPSHOT_REPLAY_OPS = 2000;

    private final CollaborativeDocumentMybatisMapper documentMapper;
    private final DocumentRevisionMybatisMapper revisionMapper;
    private final DocumentOperationMybatisMapper operationMapper;
    private final DocumentCommentMybatisMapper commentMapper;
    private final DocumentPermissionService permissionService;
    private final DocumentCacheService cacheService;
    private final DocumentEventPublisher eventPublisher;

    public DocumentOperationService(CollaborativeDocumentMybatisMapper documentMapper,
                                    DocumentRevisionMybatisMapper revisionMapper,
                                    DocumentOperationMybatisMapper operationMapper,
                                    DocumentCommentMybatisMapper commentMapper,
                                    DocumentPermissionService permissionService,
                                    DocumentCacheService cacheService,
                                    DocumentEventPublisher eventPublisher) {
        this.documentMapper = documentMapper;
        this.revisionMapper = revisionMapper;
        this.operationMapper = operationMapper;
        this.commentMapper = commentMapper;
        this.permissionService = permissionService;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
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
        Long normalizedDocId = normalizeDocumentId(documentId);
        Integer normalizedBaseRevision = normalizeBaseRevision(baseRevision);
        String normalizedSessionId = normalizeLogicalSessionId(logicalSessionId);
        Long normalizedClientSeq = normalizeClientSeq(clientSeq);
        String normalizedEditorId = normalizeEditorId(editorId);
        String normalizedEditorName = normalizeEditorName(editorName);
        DocumentWsOperation normalizedOp = normalizeOperation(op);
        String normalizedRequestedTitle = normalizeOptionalTitle(requestedTitle);
        String normalizedRequestedChangeSummary = normalizeChangeSummary(requestedChangeSummary);
        DocumentChangeType resolvedChangeType = resolveChangeType(requestedChangeType);

        permissionService.requireCanEdit(normalizedDocId, normalizedEditorId);

        DocumentOperationEntity processed = operationMapper.selectBySessionSeq(
                normalizedDocId,
                normalizedSessionId,
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

        CollaborativeDocumentEntity current = requireDocument(normalizedDocId);
        Integer currentRevision = current.getLatestRevision();
        if (normalizedBaseRevision > currentRevision) {
            throw concurrentModification(normalizedDocId, normalizedBaseRevision, currentRevision);
        }

        DocumentWsOperation effectiveOp = copyOperation(normalizedOp);
        int effectiveBaseRevision = normalizedBaseRevision;
        if (!Objects.equals(currentRevision, normalizedBaseRevision)) {
            int lag = currentRevision - normalizedBaseRevision;
            if (lag > MAX_REBASE_OPS) {
                throw concurrentModification(normalizedDocId, normalizedBaseRevision, currentRevision);
            }
            List<DocumentOperationEntity> appliedOps = operationMapper.selectByRevisionRange(
                    normalizedDocId,
                    normalizedBaseRevision,
                    lag
            );
            if (appliedOps.size() < lag) {
                throw concurrentModification(normalizedDocId, normalizedBaseRevision, currentRevision);
            }
            effectiveOp = rebaseOperation(
                    effectiveOp,
                    appliedOps,
                    normalizedEditorId,
                    normalizedClientSeq
            );
            effectiveBaseRevision = currentRevision;
        }

        String currentContent = current.getContent() == null ? "" : current.getContent();
        String nextTitle = resolveNextTitle(current.getTitle(), normalizedRequestedTitle);
        String nextContent = applyTextOperation(currentContent, effectiveOp);
        LocalDateTime now = LocalDateTime.now();
        int nextRevision = effectiveBaseRevision + 1;

        int updated = documentMapper.conditionalUpdate(
                normalizedDocId,
                current.getVersion(),
                effectiveBaseRevision,
                nextTitle,
                nextContent,
                nextRevision,
                normalizedEditorName,
                now
        );
        if (updated == 0) {
            CollaborativeDocumentEntity latest = requireDocument(normalizedDocId);
            throw concurrentModification(normalizedDocId, effectiveBaseRevision, latest.getLatestRevision());
        }

        migrateOpenCommentAnchors(normalizedDocId, effectiveOp);

        DocumentRevisionEntity revision = new DocumentRevisionEntity();
        boolean snapshotRevision = shouldStoreSnapshotRevision(nextRevision);
        revision.setDocumentId(normalizedDocId);
        revision.setRevisionNo(nextRevision);
        revision.setBaseRevision(effectiveBaseRevision);
        revision.setTitle(nextTitle);
        revision.setContent(snapshotRevision ? nextContent : null);
        revision.setIsSnapshot(snapshotRevision);
        revision.setEditorId(normalizedEditorId);
        revision.setEditorName(normalizedEditorName);
        revision.setChangeType(resolvedChangeType);
        revision.setChangeSummary(resolveChangeSummary(normalizedRequestedChangeSummary, effectiveOp, resolvedChangeType));
        revision.setCreatedAt(now);
        revision.prepareForInsert();
        revisionMapper.insert(revision);

        DocumentOperationEntity operation = new DocumentOperationEntity();
        operation.setDocumentId(normalizedDocId);
        operation.setRevisionNo(nextRevision);
        operation.setBaseRevision(effectiveBaseRevision);
        operation.setSessionId(normalizedSessionId);
        operation.setClientSeq(normalizedClientSeq);
        operation.setOpType(effectiveOp.getOpType());
        operation.setOpPosition(effectiveOp.getPosition());
        operation.setOpLength(effectiveOp.getLength() == null ? 0 : effectiveOp.getLength());
        operation.setOpText(effectiveOp.getText());
        operation.setEditorId(normalizedEditorId);
        operation.setEditorName(normalizedEditorName);
        operation.setCreatedAt(now);
        operation.prepareForInsert();

        try {
            operationMapper.insert(operation);
        } catch (DataIntegrityViolationException ex) {
            DocumentOperationEntity duplicated = operationMapper.selectBySessionSeq(
                    normalizedDocId,
                    normalizedSessionId,
                    normalizedClientSeq
            ).orElseThrow(() -> ex);
            CollaborativeDocumentEntity saved = requireDocument(normalizedDocId);
            cacheService.put(saved);
            return new ApplyResult(
                    true,
                    toDocument(saved),
                    toOperation(duplicated),
                    toRevision(revision)
            );
        }

        CollaborativeDocumentEntity saved = requireDocument(normalizedDocId);
        cacheService.put(saved);
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                resolveDomainEventType(resolvedChangeType),
                normalizedDocId,
                nextRevision,
                normalizedEditorId,
                normalizedEditorName,
                buildOperationPayload(operation, effectiveBaseRevision, normalizedClientSeq, resolvedChangeType)
        ));
        return new ApplyResult(
                false,
                toDocument(saved),
                toOperation(operation),
                toRevision(revision)
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
                changeType
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
        return operationMapper.selectByRevisionRange(normalizedDocId, normalizedFromRevision, normalizedLimit)
                .stream()
                .map(this::toOperation)
                .toList();
    }

    private String applyTextOperation(String content, DocumentWsOperation op) {
        int textLength = content.length();
        int position = op.getPosition();
        int length = op.getLength() == null ? 0 : op.getLength();
        String text = op.getText() == null ? "" : op.getText();

        if (position < 0 || position > textLength) {
            throw new BusinessException("DOCUMENT_INVALID_OPERATION", "operation position out of range");
        }
        if (length < 0 || position + length > textLength) {
            throw new BusinessException("DOCUMENT_INVALID_OPERATION", "operation length out of range");
        }

        return switch (op.getOpType()) {
            case INSERT -> content.substring(0, position) + text + content.substring(position);
            case DELETE -> content.substring(0, position) + content.substring(position + length);
            case REPLACE -> content.substring(0, position) + text + content.substring(position + length);
        };
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

    private DocumentWsOperation rebaseOperation(DocumentWsOperation incoming,
                                                List<DocumentOperationEntity> appliedOps,
                                                String incomingEditorId,
                                                Long incomingClientSeq) {
        DocumentWsOperation rebased = copyOperation(incoming);
        for (DocumentOperationEntity applied : appliedOps) {
            int appliedPos = safeNonNegative(applied.getOpPosition());
            int appliedLen = safeNonNegative(applied.getOpLength());
            int appliedInsertLen = safeTextLength(applied.getOpText());
            switch (applied.getOpType()) {
                case INSERT -> transformAgainstInsert(
                        rebased,
                        appliedPos,
                        appliedInsertLen,
                        applied.getEditorId(),
                        applied.getClientSeq(),
                        incomingEditorId,
                        incomingClientSeq
                );
                case DELETE -> transformAgainstDelete(rebased, appliedPos, appliedLen);
                case REPLACE -> {
                    transformAgainstDelete(rebased, appliedPos, appliedLen);
                    transformAgainstInsert(
                            rebased,
                            appliedPos,
                            appliedInsertLen,
                            applied.getEditorId(),
                            applied.getClientSeq(),
                            incomingEditorId,
                            incomingClientSeq
                    );
                }
            }
        }
        return rebased;
    }

    private void transformAgainstInsert(DocumentWsOperation incoming,
                                        int appliedPos,
                                        int insertedLen,
                                        String appliedEditorId,
                                        Long appliedClientSeq,
                                        String incomingEditorId,
                                        Long incomingClientSeq) {
        if (insertedLen <= 0) {
            return;
        }
        int start = safeNonNegative(incoming.getPosition());
        int length = safeNonNegative(incoming.getLength());
        int end = start + length;

        switch (incoming.getOpType()) {
            case INSERT -> {
                boolean shouldShift = appliedPos < start
                        || (appliedPos == start
                        && shouldShiftOnEqualPosition(appliedEditorId, appliedClientSeq, incomingEditorId, incomingClientSeq));
                if (shouldShift) {
                    incoming.setPosition(start + insertedLen);
                }
            }
            case DELETE, REPLACE -> {
                if (appliedPos <= start) {
                    start += insertedLen;
                } else if (appliedPos < end) {
                    length += insertedLen;
                }
                incoming.setPosition(start);
                incoming.setLength(length);
            }
        }
    }

    private void transformAgainstDelete(DocumentWsOperation incoming,
                                        int appliedPos,
                                        int deletedLen) {
        if (deletedLen <= 0) {
            return;
        }
        int deleteStart = appliedPos;
        int deleteEnd = appliedPos + deletedLen;
        int start = safeNonNegative(incoming.getPosition());
        int length = safeNonNegative(incoming.getLength());
        int end = start + length;

        switch (incoming.getOpType()) {
            case INSERT -> incoming.setPosition(transformPointByDelete(start, deleteStart, deleteEnd));
            case DELETE, REPLACE -> {
                int newStart = transformPointByDelete(start, deleteStart, deleteEnd);
                int newEnd = transformPointByDelete(end, deleteStart, deleteEnd);
                if (newEnd < newStart) {
                    newEnd = newStart;
                }
                incoming.setPosition(newStart);
                incoming.setLength(newEnd - newStart);
            }
        }
    }

    private int transformPointByDelete(int point, int deleteStart, int deleteEnd) {
        if (point >= deleteEnd) {
            return point - (deleteEnd - deleteStart);
        }
        if (point <= deleteStart) {
            return point;
        }
        return deleteStart;
    }

    private boolean shouldShiftOnEqualPosition(String appliedEditorId,
                                               Long appliedClientSeq,
                                               String incomingEditorId,
                                               Long incomingClientSeq) {
        int editorCmp = safeString(appliedEditorId).compareTo(safeString(incomingEditorId));
        if (editorCmp < 0) {
            return true;
        }
        if (editorCmp > 0) {
            return false;
        }
        return safeLong(appliedClientSeq) < safeLong(incomingClientSeq);
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
        String rebuilt = snapshot.getContent() == null ? "" : snapshot.getContent();
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
            DocumentOperationEntity operation = operationMapper.selectByRevision(documentId, replayRevision.getRevisionNo())
                    .orElseThrow(() -> new BusinessException(
                            "DOCUMENT_OPERATION_NOT_FOUND",
                            "replay operation missing, documentId=" + documentId
                                    + ", revisionNo=" + replayRevision.getRevisionNo()
                    ));
            rebuilt = applyTextOperation(rebuilt, toWsOperation(operation));
        }
        return rebuilt;
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
                                                      DocumentChangeType changeType) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("operationId", operation.getId());
        payload.put("baseRevision", baseRevision);
        payload.put("clientSeq", clientSeq);
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

    public record ApplyResult(
            boolean duplicated,
            CollaborativeDocument document,
            DocumentOperation operation,
            DocumentRevision revision
    ) {
    }
}


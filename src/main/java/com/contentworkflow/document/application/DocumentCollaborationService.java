package com.contentworkflow.document.application;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentDomainEvent;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.application.realtime.crdt.CrdtSnapshotCodec;
import com.contentworkflow.document.application.storage.DocumentDeltaStore;
import com.contentworkflow.document.application.storage.DocumentSnapshotStore;
import com.contentworkflow.document.domain.entity.CollaborativeDocument;
import com.contentworkflow.document.domain.entity.DocumentMember;
import com.contentworkflow.document.domain.entity.DocumentRevision;
import com.contentworkflow.document.domain.enums.DocumentChangeType;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentRevisionEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentRevisionMybatisMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentCollaborationService {

    private static final int MAX_REVISION_QUERY_LIMIT = 200;
    private static final int MAX_LIST_LIMIT = 100;
    private static final int SNAPSHOT_INTERVAL = resolveSnapshotInterval();
    private static final int MAX_SNAPSHOT_REPLAY_OPS = 2000;
    private static final CrdtSnapshotCodec CRDT_SNAPSHOT_CODEC = new CrdtSnapshotCodec();

    private final CollaborativeDocumentMybatisMapper documentMapper;
    private final DocumentRevisionMybatisMapper revisionMapper;
    private final DocumentDeltaStore deltaStore;
    private final DocumentPermissionService permissionService;
    private final DocumentCacheService cacheService;
    private final DocumentEventPublisher eventPublisher;
    private final DocumentSnapshotStore snapshotStore;

    @Autowired
    public DocumentCollaborationService(CollaborativeDocumentMybatisMapper documentMapper,
                                        DocumentRevisionMybatisMapper revisionMapper,
                                        DocumentDeltaStore deltaStore,
                                        DocumentPermissionService permissionService,
                                        DocumentCacheService cacheService,
                                        DocumentEventPublisher eventPublisher,
                                        DocumentSnapshotStore snapshotStore) {
        this.documentMapper = documentMapper;
        this.revisionMapper = revisionMapper;
        this.deltaStore = deltaStore;
        this.permissionService = permissionService;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
        this.snapshotStore = snapshotStore;
    }

    @Transactional
    public CollaborativeDocument createDocument(String docNo,
                                                String title,
                                                String content,
                                                String editorId,
                                                String editorName) {
        String normalizedTitle = requireText(title, "title must not be blank");
        String normalizedContent = content == null ? "" : content;
        String normalizedEditorId = normalizeEditorId(editorId);
        String normalizedEditorName = normalizeEditorName(editorName);

        CollaborativeDocumentEntity entity = new CollaborativeDocumentEntity();
        entity.setDocNo(normalizeDocNo(docNo));
        entity.setTitle(normalizedTitle);
        entity.setContent(normalizedContent);
        entity.setLatestRevision(1);
        entity.setCreatedBy(normalizedEditorName);
        entity.setUpdatedBy(normalizedEditorName);
        entity.prepareForInsert();
        documentMapper.insert(entity);

        DocumentRevisionEntity revision = new DocumentRevisionEntity();
        revision.setDocumentId(entity.getId());
        revision.setRevisionNo(1);
        revision.setBaseRevision(0);
        revision.setTitle(normalizedTitle);
        // Snapshot body is persisted via snapshotStore; revision table keeps metadata catalog only.
        revision.setContent(null);
        revision.setIsSnapshot(true);
        revision.setEditorId(normalizedEditorId);
        revision.setEditorName(normalizedEditorName);
        revision.setChangeType(DocumentChangeType.CREATE);
        revision.setChangeSummary("document created");
        revision.prepareForInsert();
        revisionMapper.insert(revision);

        String initialSnapshotRef = buildSnapshotRef(entity.getId(), revision.getRevisionNo());
        persistLatestSnapshot(entity.getId(), revision.getRevisionNo(), initialSnapshotRef, normalizedContent, normalizedEditorName);
        entity.setLatestSnapshotRef(initialSnapshotRef);
        entity.setLatestSnapshotRevision(revision.getRevisionNo());

        permissionService.addOwnerMember(entity.getId(), normalizedEditorId, normalizedEditorName);
        cacheService.put(entity);
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                "DOCUMENT_CREATED",
                entity.getId(),
                1,
                normalizedEditorId,
                normalizedEditorName,
                Map.of("docNo", entity.getDocNo(), "title", normalizedTitle)
        ));
        return toDocument(entity);
    }

    @Transactional(readOnly = true)
    public List<CollaborativeDocument> listDocuments(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_LIST_LIMIT));
        return documentMapper.selectLatest(normalizedLimit).stream().map(this::toDocument).toList();
    }

    @Transactional(readOnly = true)
    public List<CollaborativeDocument> listDocumentsByMember(String memberId, int limit) {
        String normalizedMemberId = normalizeEditorId(memberId);
        int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_LIST_LIMIT));
        return documentMapper.selectLatestByMember(normalizedMemberId, normalizedLimit)
                .stream()
                .map(this::toDocument)
                .toList();
    }

    @Transactional(readOnly = true)
    public CollaborativeDocument getDocument(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "documentId must be > 0");
        }
        CollaborativeDocumentEntity cached = cacheService.get(documentId);
        if (cached != null) {
            return toDocument(cached);
        }
        CollaborativeDocumentEntity entity = requireDocument(documentId);
        cacheService.put(entity);
        return toDocument(entity);
    }

    @Transactional
    public DocumentEditResult editDocument(Long documentId,
                                           Integer baseRevision,
                                           String title,
                                           String content,
                                           String editorId,
                                           String editorName,
                                           String changeSummary,
                                           DocumentChangeType changeType) {
        CollaborativeDocumentEntity current = requireDocument(documentId);
        int normalizedBaseRevision = normalizeBaseRevision(baseRevision);
        String normalizedEditorId = normalizeEditorId(editorId);
        String normalizedEditorName = normalizeEditorName(editorName);
        permissionService.requireCanEdit(documentId, normalizedEditorId);
        if (!Objects.equals(current.getLatestRevision(), normalizedBaseRevision)) {
            throw concurrentModification(documentId, normalizedBaseRevision, current.getLatestRevision());
        }

        String normalizedTitle = requireText(title, "title must not be blank");
        String normalizedContent = content == null ? "" : content;
        LocalDateTime now = LocalDateTime.now();
        int nextRevision = normalizedBaseRevision + 1;

        int updated = documentMapper.conditionalUpdate(
                documentId,
                current.getVersion(),
                normalizedBaseRevision,
                normalizedTitle,
                normalizedContent,
                nextRevision,
                normalizedEditorName,
                now
        );
        if (updated == 0) {
            CollaborativeDocumentEntity latest = requireDocument(documentId);
            throw concurrentModification(documentId, normalizedBaseRevision, latest.getLatestRevision());
        }

        DocumentChangeType resolvedChangeType = changeType == null ? DocumentChangeType.EDIT : changeType;
        DocumentRevisionEntity revision = new DocumentRevisionEntity();
        boolean snapshotRevision = shouldStoreSnapshotRevision(nextRevision);
        revision.setDocumentId(documentId);
        revision.setRevisionNo(nextRevision);
        revision.setBaseRevision(normalizedBaseRevision);
        revision.setTitle(normalizedTitle);
        // Snapshot body is persisted via snapshotStore; revision table keeps metadata catalog only.
        revision.setContent(null);
        revision.setIsSnapshot(snapshotRevision);
        revision.setEditorId(normalizedEditorId);
        revision.setEditorName(normalizedEditorName);
        revision.setChangeType(resolvedChangeType);
        revision.setChangeSummary(normalizeChangeSummary(changeSummary));
        revision.prepareForInsert();
        revisionMapper.insert(revision);

        DocumentOperationEntity operation = new DocumentOperationEntity();
        operation.setDocumentId(documentId);
        operation.setRevisionNo(nextRevision);
        operation.setBaseRevision(normalizedBaseRevision);
        operation.setSessionId("legacy-edit-" + documentId);
        operation.setClientSeq((long) nextRevision);
        operation.setOpType(DocumentOpType.REPLACE);
        operation.setOpPosition(0);
        operation.setOpLength(current.getContent() == null ? 0 : current.getContent().length());
        operation.setOpText(normalizedContent);
        operation.setEditorId(normalizedEditorId);
        operation.setEditorName(normalizedEditorName);
        operation.setCreatedAt(now);
        operation.prepareForInsert();
        deltaStore.appendIfAbsent(operation);
        if (snapshotRevision) {
            String snapshotRef = buildSnapshotRef(documentId, nextRevision);
            persistLatestSnapshot(documentId, nextRevision, snapshotRef, normalizedContent, normalizedEditorName);
        }

        CollaborativeDocumentEntity saved = requireDocument(documentId);
        cacheService.put(saved);
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                resolvedChangeType == DocumentChangeType.RESTORE ? "DOCUMENT_RESTORED" : "DOCUMENT_EDITED",
                documentId,
                nextRevision,
                normalizedEditorId,
                normalizedEditorName,
                Map.of(
                        "baseRevision", normalizedBaseRevision,
                        "nextRevision", nextRevision,
                        "changeType", resolvedChangeType.name()
                )
        ));
        return new DocumentEditResult(toDocument(saved), toRevision(revision));
    }

    @Transactional
    public DocumentEditResult restoreDocumentRevision(Long documentId,
                                                      Integer targetRevision,
                                                      Integer baseRevision,
                                                      String editorId,
                                                      String editorName,
                                                      String changeSummary) {
        String normalizedEditorId = normalizeEditorId(editorId);
        String normalizedEditorName = normalizeEditorName(editorName);
        permissionService.requireOwner(documentId, normalizedEditorId);
        int normalizedTargetRevision = normalizeBaseRevision(targetRevision);
        DocumentRevision target = getRevision(documentId, normalizedTargetRevision);
        return editDocument(
                documentId,
                baseRevision,
                target.getTitle(),
                target.getContent(),
                normalizedEditorId,
                normalizedEditorName,
                changeSummary == null || changeSummary.isBlank()
                        ? "restore from revision " + normalizedTargetRevision
                        : changeSummary,
                DocumentChangeType.RESTORE
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentRevision> listRevisions(Long documentId, int limit) {
        requireDocument(documentId);
        int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, MAX_REVISION_QUERY_LIMIT));
        return revisionMapper.selectByDocumentIdOrderByRevisionDesc(documentId, normalizedLimit).stream()
                .map(this::toRevision)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentRevision getRevision(Long documentId, Integer revisionNo) {
        requireDocument(documentId);
        int normalizedRevisionNo = normalizeBaseRevision(revisionNo);
        DocumentRevisionEntity revision = revisionMapper.selectByDocumentIdAndRevisionNo(documentId, normalizedRevisionNo)
                .orElseThrow(() -> new BusinessException("DOCUMENT_REVISION_NOT_FOUND", "target revision not found"));
        if (revision.getContent() == null) {
            revision.setContent(rebuildRevisionContent(documentId, revision));
        }
        return toRevision(revision);
    }

    @Transactional(readOnly = true)
    public List<DocumentMember> listMembers(Long documentId) {
        requireDocument(documentId);
        return permissionService.listMembers(documentId);
    }

    @Transactional
    public DocumentMember upsertMember(Long documentId,
                                       String memberId,
                                       String memberName,
                                       DocumentMemberRole memberRole,
                                       String operatorId) {
        requireDocument(documentId);
        DocumentMember member = permissionService.upsertMember(
                documentId,
                normalizeEditorId(operatorId),
                memberId,
                memberName,
                memberRole
        );
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                "DOCUMENT_MEMBER_UPSERTED",
                documentId,
                null,
                normalizeEditorId(operatorId),
                normalizeEditorId(operatorId),
                Map.of(
                        "memberId", member.getMemberId(),
                        "memberRole", member.getMemberRole().name()
                )
        ));
        return member;
    }

    private CollaborativeDocument toDocument(CollaborativeDocumentEntity entity) {
        return CollaborativeDocument.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .docNo(entity.getDocNo())
                .title(entity.getTitle())
                // Keep returning table content for compatibility; snapshot reference path is now prepared.
                .content(entity.getContent())
                .latestRevision(entity.getLatestRevision())
                .latestSnapshotRef(entity.getLatestSnapshotRef())
                .latestSnapshotRevision(entity.getLatestSnapshotRevision())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
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

    private CollaborativeDocumentEntity requireDocument(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "documentId must be > 0");
        }
        CollaborativeDocumentEntity entity = documentMapper.selectById(documentId);
        if (entity == null) {
            throw new BusinessException("DOCUMENT_NOT_FOUND", "document not found");
        }
        return entity;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("INVALID_ARGUMENT", message);
        }
        return value.trim();
    }

    private int normalizeBaseRevision(Integer baseRevision) {
        if (baseRevision == null || baseRevision <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "baseRevision must be >= 1");
        }
        return baseRevision;
    }

    private String normalizeDocNo(String docNo) {
        if (docNo != null && !docNo.isBlank()) {
            return docNo.trim();
        }
        return "DOC-" + UUID.randomUUID().toString().replace("-", "");
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

    private String normalizeChangeSummary(String changeSummary) {
        if (changeSummary == null) {
            return null;
        }
        String normalized = changeSummary.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private BusinessException concurrentModification(Long documentId, int baseRevision, Integer latestRevision) {
        return new BusinessException(
                "DOCUMENT_CONCURRENT_MODIFICATION",
                "stale baseRevision, documentId=" + documentId
                        + ", baseRevision=" + baseRevision
                        + ", latestRevision=" + latestRevision
        );
    }

    private boolean shouldStoreSnapshotRevision(int revisionNo) {
        return revisionNo == 1 || revisionNo % SNAPSHOT_INTERVAL == 0;
    }

    private String rebuildRevisionContent(Long documentId, DocumentRevisionEntity targetRevision) {
        DocumentRevisionEntity snapshot = revisionMapper.selectLatestSnapshotByRevision(documentId, targetRevision.getRevisionNo())
                .orElseThrow(() -> new BusinessException(
                        "DOCUMENT_SNAPSHOT_NOT_FOUND",
                        "snapshot not found, documentId=" + documentId + ", revisionNo=" + targetRevision.getRevisionNo()
                ));
        String rebuilt = loadSnapshotContent(documentId, snapshot);
        int replayCount = targetRevision.getRevisionNo() - snapshot.getRevisionNo();
        if (replayCount <= 0) {
            return rebuilt;
        }
        if (replayCount > MAX_SNAPSHOT_REPLAY_OPS) {
            throw new BusinessException(
                    "DOCUMENT_REPLAY_LIMIT_EXCEEDED",
                    "replay exceeds limit, documentId=" + documentId
                            + ", fromRevision=" + snapshot.getRevisionNo()
                            + ", targetRevision=" + targetRevision.getRevisionNo()
            );
        }
        List<DocumentRevisionEntity> revisionsToReplay = revisionMapper.selectByRevisionRangeAsc(
                documentId,
                snapshot.getRevisionNo(),
                targetRevision.getRevisionNo(),
                replayCount
        );
        if (revisionsToReplay.size() < replayCount) {
            throw new BusinessException(
                    "DOCUMENT_REVISION_NOT_FOUND",
                    "replay revisions not complete, documentId=" + documentId
                            + ", fromRevision=" + snapshot.getRevisionNo()
                            + ", targetRevision=" + targetRevision.getRevisionNo()
            );
        }
        for (DocumentRevisionEntity replayRevision : revisionsToReplay) {
            DocumentOperationEntity operation = deltaStore.findByRevision(documentId, replayRevision.getRevisionNo())
                    .orElseThrow(() -> new BusinessException(
                            "DOCUMENT_OPERATION_NOT_FOUND",
                            "replay operation missing, documentId=" + documentId
                                    + ", revisionNo=" + replayRevision.getRevisionNo()
                    ));
            rebuilt = applyOperation(rebuilt, operation);
        }
        return rebuilt;
    }

    private String applyOperation(String content, DocumentOperationEntity operation) {
        String safeContent = content == null ? "" : content;
        int textLength = safeContent.length();
        int position = operation.getOpPosition() == null ? 0 : operation.getOpPosition();
        int length = operation.getOpLength() == null ? 0 : operation.getOpLength();
        String text = operation.getOpText() == null ? "" : operation.getOpText();

        if (position < 0 || position > textLength) {
            throw new BusinessException("DOCUMENT_INVALID_OPERATION", "operation position out of range");
        }
        if (length < 0 || position + length > textLength) {
            throw new BusinessException("DOCUMENT_INVALID_OPERATION", "operation length out of range");
        }
        return switch (operation.getOpType()) {
            case INSERT -> safeContent.substring(0, position) + text + safeContent.substring(position);
            case DELETE -> safeContent.substring(0, position) + safeContent.substring(position + length);
            case REPLACE -> safeContent.substring(0, position) + text + safeContent.substring(position + length);
        };
    }

    private static int resolveSnapshotInterval() {
        Integer configured = Integer.getInteger("document.snapshot.interval");
        if (configured == null || configured <= 0) {
            return 100;
        }
        return configured;
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

        String snapshotRef = buildSnapshotRef(documentId, snapshotRevision.getRevisionNo());
        Optional<String> loaded = snapshotStore.get(snapshotRef);
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

    public record DocumentEditResult(
            CollaborativeDocument document,
            DocumentRevision revision
    ) {
    }
}

package com.contentworkflow.document.application.gc;

import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.event.DocumentDomainEvent;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.application.realtime.crdt.CrdtSnapshotCodec;
import com.contentworkflow.document.application.storage.DocumentSnapshotStore;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class SnapshotCompactionExecutor implements DocumentCompactionExecutor {

    private static final Logger log = LoggerFactory.getLogger(SnapshotCompactionExecutor.class);
    private static final CrdtSnapshotCodec CRDT_SNAPSHOT_CODEC = new CrdtSnapshotCodec();
    private static final String TOMBSTONE_GC_TRIGGER_PREFIX = "TOMBSTONE_GC";

    private final CollaborativeDocumentMybatisMapper documentMapper;
    private final DocumentSnapshotStore snapshotStore;
    private final DocumentCacheService cacheService;
    private final DocumentEventPublisher eventPublisher;

    public SnapshotCompactionExecutor(CollaborativeDocumentMybatisMapper documentMapper,
                                      DocumentSnapshotStore snapshotStore,
                                      DocumentCacheService cacheService,
                                      DocumentEventPublisher eventPublisher) {
        this.documentMapper = documentMapper;
        this.snapshotStore = snapshotStore;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(DocumentCompactionTask task) {
        if (task == null || task.documentId() == null || task.documentId() <= 0) {
            return;
        }
        if (isInvalidGcTask(task)) {
            log.warn("invalid tombstone gc compaction task ignored, docId={}, trigger={}, upperClock={}",
                    task.documentId(), task.trigger(), task.segmentUpperClockInclusive());
            return;
        }
        Long documentId = task.documentId();
        CollaborativeDocumentEntity document = documentMapper.selectById(documentId);
        if (document == null || document.getLatestRevision() == null || document.getLatestRevision() <= 0) {
            return;
        }

        Integer latestRevision = document.getLatestRevision();
        String snapshotRef = "snapshot/" + documentId + "/" + latestRevision + ".bin";
        BaselineSnapshot baselineSnapshot = rebuildVisibleBaseline(document);
        String latestContent = baselineSnapshot.visibleText();
        String updatedBy = normalizeUpdatedBy(document.getUpdatedBy());
        String encodedPayload = CRDT_SNAPSHOT_CODEC.encodeText(latestContent);

        boolean snapshotReused = isSnapshotPayloadUpToDate(snapshotRef, encodedPayload);
        if (!snapshotReused) {
            snapshotStore.put(snapshotRef, encodedPayload);
        }
        boolean metadataUpdated = updateSnapshotMetadataIfRequired(document, snapshotRef, latestRevision, updatedBy);
        if (!snapshotReused || metadataUpdated) {
            cacheService.evict(documentId);
        }
        publishCompletedEvent(task, snapshotRef, latestRevision, updatedBy, baselineSnapshot, encodedPayload, snapshotReused, metadataUpdated);
    }

    private void publishCompletedEvent(DocumentCompactionTask task,
                                       String snapshotRef,
                                       Integer latestRevision,
                                       String updatedBy,
                                       BaselineSnapshot baselineSnapshot,
                                       String encodedPayload,
                                       boolean snapshotReused,
                                       boolean metadataUpdated) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshotRef", snapshotRef);
        payload.put("trigger", task.trigger());
        payload.put("createdAt", task.createdAt());
        payload.put("segmentUpperClockInclusive", task.segmentUpperClockInclusive());
        payload.put("sourceType", baselineSnapshot.sourceType());
        payload.put("sourceSnapshotRef", baselineSnapshot.sourceSnapshotRef());
        payload.put("snapshotReused", snapshotReused);
        payload.put("metadataUpdated", metadataUpdated);
        payload.put("visibleCharCount", baselineSnapshot.visibleText().length());
        payload.put("payloadBytes", encodedPayload.getBytes(StandardCharsets.UTF_8).length);
        payload.put("payloadSha256", sha256Hex(encodedPayload));
        eventPublisher.publish(DocumentDomainEvent.of(
                "DOCUMENT_COMPACTION_COMPLETED",
                task.documentId(),
                latestRevision,
                "system",
                updatedBy,
                payload
        ));
    }

    private BaselineSnapshot rebuildVisibleBaseline(CollaborativeDocumentEntity document) {
        String contentFromDocument = safeContent(document.getContent());
        String sourceSnapshotRef = normalizeSnapshotRef(document.getLatestSnapshotRef());
        if (sourceSnapshotRef == null) {
            return new BaselineSnapshot(contentFromDocument, "document_content", null);
        }

        Optional<String> loadedSnapshot = getSnapshotSafely(sourceSnapshotRef);
        if (loadedSnapshot.isEmpty()) {
            log.warn("compaction baseline snapshot missing, docId={}, snapshotRef={}, fallback=document_content",
                    document.getId(),
                    sourceSnapshotRef);
            return new BaselineSnapshot(contentFromDocument, "document_content", sourceSnapshotRef);
        }

        String snapshotVisibleText;
        try {
            snapshotVisibleText = decodeSnapshotVisibleText(loadedSnapshot.get());
        } catch (Exception ex) {
            log.warn("compaction baseline snapshot decode failed, docId={}, snapshotRef={}, fallback=document_content",
                    document.getId(),
                    sourceSnapshotRef,
                    ex);
            return new BaselineSnapshot(contentFromDocument, "document_content", sourceSnapshotRef);
        }

        if (!Objects.equals(contentFromDocument, snapshotVisibleText)) {
            if (contentFromDocument.isEmpty() && !snapshotVisibleText.isEmpty()) {
                return new BaselineSnapshot(snapshotVisibleText, "latest_snapshot_ref", sourceSnapshotRef);
            }
            log.warn("compaction baseline mismatch, docId={}, revision={}, snapshotRef={}, fallback=document_content",
                    document.getId(),
                    document.getLatestRevision(),
                    sourceSnapshotRef);
            return new BaselineSnapshot(contentFromDocument, "document_content", sourceSnapshotRef);
        }
        return new BaselineSnapshot(snapshotVisibleText, "latest_snapshot_ref", sourceSnapshotRef);
    }

    private boolean isSnapshotPayloadUpToDate(String snapshotRef, String encodedPayload) {
        return getSnapshotSafely(snapshotRef)
                .map(encodedPayload::equals)
                .orElse(false);
    }

    private boolean updateSnapshotMetadataIfRequired(CollaborativeDocumentEntity document,
                                                     String snapshotRef,
                                                     Integer latestRevision,
                                                     String updatedBy) {
        if (Objects.equals(document.getLatestSnapshotRef(), snapshotRef)
                && Objects.equals(document.getLatestSnapshotRevision(), latestRevision)) {
            return false;
        }
        documentMapper.updateSnapshotMetadata(
                document.getId(),
                snapshotRef,
                latestRevision,
                updatedBy,
                LocalDateTime.now()
        );
        return true;
    }

    private String decodeSnapshotVisibleText(String snapshotPayload) {
        if (!CRDT_SNAPSHOT_CODEC.isEncodedPayload(snapshotPayload)) {
            return safeContent(snapshotPayload);
        }
        return CRDT_SNAPSHOT_CODEC.decodeToText(snapshotPayload);
    }

    private Optional<String> getSnapshotSafely(String snapshotRef) {
        Optional<String> loaded = snapshotStore.get(snapshotRef);
        return loaded == null ? Optional.empty() : loaded;
    }

    private String safeContent(String content) {
        return content == null ? "" : content;
    }

    private String normalizeSnapshotRef(String snapshotRef) {
        if (snapshotRef == null) {
            return null;
        }
        String normalized = snapshotRef.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sha256Hex(String value) {
        byte[] content = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-256 not available", ex);
        }
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte next : hash) {
            builder.append(Character.forDigit((next >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(next & 0xF, 16));
        }
        return builder.toString();
    }

    private String normalizeUpdatedBy(String updatedBy) {
        if (updatedBy == null || updatedBy.isBlank()) {
            return "system";
        }
        return updatedBy.trim();
    }

    private boolean isInvalidGcTask(DocumentCompactionTask task) {
        String trigger = task.trigger();
        if (trigger == null || !trigger.startsWith(TOMBSTONE_GC_TRIGGER_PREFIX)) {
            return false;
        }
        Long upperClock = task.segmentUpperClockInclusive();
        return upperClock == null || upperClock <= 0L;
    }

    private record BaselineSnapshot(String visibleText, String sourceType, String sourceSnapshotRef) {
    }
}

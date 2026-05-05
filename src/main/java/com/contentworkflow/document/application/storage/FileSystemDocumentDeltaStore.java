package com.contentworkflow.document.application.storage;

import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Filesystem-backed delta store (append-only JSONL by document).
 * This is an intermediate non-MySQL backend for hot incremental writes.
 */
@Component
@ConditionalOnProperty(prefix = "workflow.operation-log", name = "backend", havingValue = "filesystem")
public class FileSystemDocumentDeltaStore implements DocumentDeltaStore {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDocumentDeltaStore.class);

    private final ObjectMapper objectMapper;
    private final Path rootDirectory;
    private final ConcurrentHashMap<Long, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong syntheticId = new AtomicLong(1L);

    public FileSystemDocumentDeltaStore(
            ObjectMapper objectMapper,
            @Value("${workflow.operation-log.filesystem.root-dir:./data/operation-log}") String rootDirectory) {
        this.objectMapper = objectMapper;
        this.rootDirectory = resolveRootDirectory(rootDirectory);
        try {
            Files.createDirectories(this.rootDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to initialize delta-log filesystem root: " + this.rootDirectory, ex);
        }
        log.info("document delta store backend=filesystem root={}", this.rootDirectory.toAbsolutePath());
    }

    @Override
    public Optional<DocumentOperationEntity> findBySessionSeq(Long documentId, String sessionId, Long clientSeq) {
        if (!isValidDocumentId(documentId) || isBlank(sessionId) || clientSeq == null || clientSeq <= 0) {
            return Optional.empty();
        }
        ReentrantReadWriteLock.ReadLock readLock = lockFor(documentId).readLock();
        readLock.lock();
        try {
            return readAll(documentId).stream()
                    .filter(op -> sessionId.trim().equals(op.getSessionId()) && clientSeq.equals(op.getClientSeq()))
                    .findFirst();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Optional<DocumentOperationEntity> findByRevision(Long documentId, Integer revisionNo) {
        if (!isValidDocumentId(documentId) || revisionNo == null || revisionNo <= 0) {
            return Optional.empty();
        }
        ReentrantReadWriteLock.ReadLock readLock = lockFor(documentId).readLock();
        readLock.lock();
        try {
            return readAll(documentId).stream()
                    .filter(op -> revisionNo.equals(op.getRevisionNo()))
                    .findFirst();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<DocumentOperationEntity> listByRevisionRange(Long documentId, Integer fromRevisionExclusive, Integer limit) {
        if (!isValidDocumentId(documentId)) {
            return List.of();
        }
        int fromRevision = fromRevisionExclusive == null ? 0 : Math.max(0, fromRevisionExclusive);
        int replayLimit = limit == null ? 200 : Math.max(1, limit);
        ReentrantReadWriteLock.ReadLock readLock = lockFor(documentId).readLock();
        readLock.lock();
        try {
            return readAll(documentId).stream()
                    .filter(op -> op.getRevisionNo() != null && op.getRevisionNo() > fromRevision)
                    .sorted(Comparator.comparing(DocumentOperationEntity::getRevisionNo))
                    .limit(replayLimit)
                    .toList();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public AppendResult appendIfAbsent(DocumentOperationEntity operation) {
        DocumentOperationEntity normalized = requireAppendArguments(operation);
        ReentrantReadWriteLock.WriteLock writeLock = lockFor(normalized.getDocumentId()).writeLock();
        writeLock.lock();
        try {
            List<DocumentOperationEntity> existing = readAll(normalized.getDocumentId());
            Optional<DocumentOperationEntity> duplicated = existing.stream()
                    .filter(op -> normalized.getSessionId().trim().equals(op.getSessionId())
                            && normalized.getClientSeq().equals(op.getClientSeq()))
                    .findFirst();
            if (duplicated.isPresent()) {
                return new AppendResult(true, duplicated.get());
            }

            normalized.setSessionId(normalized.getSessionId().trim());
            normalized.setCreatedAt(normalized.getCreatedAt() == null ? LocalDateTime.now() : normalized.getCreatedAt());
            if (normalized.getId() == null || normalized.getId() <= 0) {
                normalized.setId(nextSyntheticId(normalized.getDocumentId(), normalized.getRevisionNo()));
            }

            appendLine(normalized.getDocumentId(), objectMapper.writeValueAsString(normalized));
            return new AppendResult(false, normalized);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to append delta to filesystem store", ex);
        } finally {
            writeLock.unlock();
        }
    }

    private void appendLine(Long documentId, String line) throws IOException {
        Path filePath = resolveDocumentFile(documentId);
        Files.createDirectories(filePath.getParent());
        Files.writeString(
                filePath,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
        );
    }

    private List<DocumentOperationEntity> readAll(Long documentId) {
        Path filePath = resolveDocumentFile(documentId);
        if (!Files.exists(filePath)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            List<DocumentOperationEntity> operations = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                operations.add(objectMapper.readValue(line, DocumentOperationEntity.class));
            }
            return operations;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read delta file: " + filePath, ex);
        }
    }

    private Path resolveRootDirectory(String configuredRoot) {
        String root = configuredRoot == null || configuredRoot.isBlank() ? "./data/operation-log" : configuredRoot.trim();
        try {
            return Paths.get(root).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new IllegalStateException("invalid delta filesystem root: " + root, ex);
        }
    }

    private Path resolveDocumentFile(Long documentId) {
        long docId = documentId == null ? 0L : documentId;
        long shard = Math.floorMod(docId, 100);
        return rootDirectory.resolve(String.format("%02d", shard)).resolve("doc-" + docId + ".jsonl");
    }

    private ReentrantReadWriteLock lockFor(Long documentId) {
        return locks.computeIfAbsent(documentId, key -> new ReentrantReadWriteLock());
    }

    private long nextSyntheticId(Long documentId, Integer revisionNo) {
        long docComponent = documentId == null ? 0L : Math.floorMod(documentId, 1_000_000L);
        long revComponent = revisionNo == null ? 0L : Math.floorMod(revisionNo.longValue(), 1_000_000L);
        return docComponent * 1_000_000_000_000L + revComponent * 1_000_000L + syntheticId.getAndIncrement();
    }

    private DocumentOperationEntity requireAppendArguments(DocumentOperationEntity operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        if (!isValidDocumentId(operation.getDocumentId())) {
            throw new IllegalArgumentException("operation.documentId must be > 0");
        }
        if (isBlank(operation.getSessionId())) {
            throw new IllegalArgumentException("operation.sessionId must not be blank");
        }
        if (operation.getClientSeq() == null || operation.getClientSeq() <= 0) {
            throw new IllegalArgumentException("operation.clientSeq must be > 0");
        }
        if (operation.getRevisionNo() == null || operation.getRevisionNo() <= 0) {
            throw new IllegalArgumentException("operation.revisionNo must be > 0");
        }
        return operation;
    }

    private boolean isValidDocumentId(Long documentId) {
        return documentId != null && documentId > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

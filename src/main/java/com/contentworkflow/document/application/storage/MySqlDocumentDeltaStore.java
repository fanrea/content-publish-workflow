package com.contentworkflow.document.application.storage;

import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentOperationMybatisMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Compatibility backend for delta store based on existing MySQL table.
 */
@Component
@ConditionalOnProperty(prefix = "workflow.operation-log", name = "backend", havingValue = "mysql")
public class MySqlDocumentDeltaStore implements DocumentDeltaStore {

    private final DocumentOperationMybatisMapper operationMapper;

    public MySqlDocumentDeltaStore(DocumentOperationMybatisMapper operationMapper) {
        this.operationMapper = operationMapper;
    }

    @Override
    public Optional<DocumentOperationEntity> findBySessionSeq(Long documentId, String sessionId, Long clientSeq) {
        if (!isValidDocumentId(documentId) || isBlank(sessionId) || clientSeq == null || clientSeq <= 0) {
            return Optional.empty();
        }
        return operationMapper.selectBySessionSeq(documentId, sessionId.trim(), clientSeq);
    }

    @Override
    public Optional<DocumentOperationEntity> findByRevision(Long documentId, Integer revisionNo) {
        if (!isValidDocumentId(documentId) || revisionNo == null || revisionNo <= 0) {
            return Optional.empty();
        }
        return operationMapper.selectByRevision(documentId, revisionNo);
    }

    @Override
    public List<DocumentOperationEntity> listByRevisionRange(Long documentId, Integer fromRevisionExclusive, Integer limit) {
        if (!isValidDocumentId(documentId)) {
            return List.of();
        }
        int fromRevision = fromRevisionExclusive == null ? 0 : Math.max(0, fromRevisionExclusive);
        int replayLimit = limit == null ? 200 : Math.max(1, limit);
        return operationMapper.selectByRevisionRange(documentId, fromRevision, replayLimit);
    }

    @Override
    public AppendResult appendIfAbsent(DocumentOperationEntity operation) {
        DocumentOperationEntity normalized = requireAppendArguments(operation);
        Optional<DocumentOperationEntity> existing = findBySessionSeq(
                normalized.getDocumentId(),
                normalized.getSessionId(),
                normalized.getClientSeq()
        );
        if (existing.isPresent()) {
            return new AppendResult(true, existing.get());
        }

        normalized.prepareForInsert();
        if (normalized.getCreatedAt() == null) {
            normalized.setCreatedAt(LocalDateTime.now());
        }
        try {
            operationMapper.insert(normalized);
            return new AppendResult(false, normalized);
        } catch (DataIntegrityViolationException ex) {
            DocumentOperationEntity duplicated = findBySessionSeq(
                    normalized.getDocumentId(),
                    normalized.getSessionId(),
                    normalized.getClientSeq()
            ).orElseThrow(() -> ex);
            return new AppendResult(true, duplicated);
        }
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

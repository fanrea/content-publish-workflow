package com.contentworkflow.document.application.storage;

import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;

import java.util.List;
import java.util.Optional;

/**
 * Operation delta storage abstraction.
 * Supports idempotent append and revision/session based lookup.
 */
public interface DocumentDeltaStore {

    Optional<DocumentOperationEntity> findBySessionSeq(Long documentId, String sessionId, Long clientSeq);

    Optional<DocumentOperationEntity> findByRevision(Long documentId, Integer revisionNo);

    List<DocumentOperationEntity> listByRevisionRange(Long documentId, Integer fromRevisionExclusive, Integer limit);

    AppendResult appendIfAbsent(DocumentOperationEntity operation);

    record AppendResult(boolean duplicated, DocumentOperationEntity operation) {
    }
}

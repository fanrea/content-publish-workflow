package com.contentworkflow.document.application.storage;

import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fallback implementation used when no concrete backend is selected.
 */
@Component
@ConditionalOnProperty(prefix = "workflow.operation-log", name = "backend", havingValue = "noop", matchIfMissing = true)
@ConditionalOnMissingBean(DocumentDeltaStore.class)
public class NoopDocumentDeltaStore implements DocumentDeltaStore {

    @Override
    public Optional<DocumentOperationEntity> findBySessionSeq(Long documentId, String sessionId, Long clientSeq) {
        return Optional.empty();
    }

    @Override
    public Optional<DocumentOperationEntity> findByRevision(Long documentId, Integer revisionNo) {
        return Optional.empty();
    }

    @Override
    public List<DocumentOperationEntity> listByRevisionRange(Long documentId, Integer fromRevisionExclusive, Integer limit) {
        return List.of();
    }

    @Override
    public AppendResult appendIfAbsent(DocumentOperationEntity operation) {
        throw new IllegalStateException("document delta store backend noop cannot accept append operations");
    }
}

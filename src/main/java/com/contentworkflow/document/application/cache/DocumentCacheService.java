package com.contentworkflow.document.application.cache;

import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;

/**
 * Document snapshot cache abstraction.
 */
public interface DocumentCacheService {

    CollaborativeDocumentEntity get(Long documentId);

    void put(CollaborativeDocumentEntity document);

    void evict(Long documentId);
}

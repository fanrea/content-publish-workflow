package com.contentworkflow.document.application.cache;

import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(DocumentCacheService.class)
public class NoopDocumentCacheService implements DocumentCacheService {

    @Override
    public CollaborativeDocumentEntity get(Long documentId) {
        return null;
    }

    @Override
    public void put(CollaborativeDocumentEntity document) {
        // no-op
    }

    @Override
    public void evict(Long documentId) {
        // no-op
    }
}

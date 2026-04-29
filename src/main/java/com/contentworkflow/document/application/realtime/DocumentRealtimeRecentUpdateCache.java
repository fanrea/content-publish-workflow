package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.domain.entity.DocumentOperation;

import java.util.List;

/**
 * Hot-window recent updates cache for websocket reconnect/sync fast path.
 */
public interface DocumentRealtimeRecentUpdateCache {

    void append(DocumentOperation operation);

    ReplayResult replaySince(Long documentId, int fromRevisionExclusive, int limit);

    record ReplayResult(
            List<DocumentOperation> operations,
            boolean completeFromBase
    ) {
    }
}

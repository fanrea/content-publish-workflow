package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.domain.entity.DocumentOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnMissingBean(DocumentRealtimeRecentUpdateCache.class)
public class NoopDocumentRealtimeRecentUpdateCache implements DocumentRealtimeRecentUpdateCache {

    private static final ReplayResult EMPTY_RESULT = new ReplayResult(List.of(), false);

    @Override
    public void append(DocumentOperation operation) {
        // no-op
    }

    @Override
    public ReplayResult replaySince(Long documentId, int fromRevisionExclusive, int limit) {
        return EMPTY_RESULT;
    }
}

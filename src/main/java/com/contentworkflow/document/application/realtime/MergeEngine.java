package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;

import java.util.List;
import java.util.Map;

public interface MergeEngine {

    String apply(String content, DocumentWsOperation op);

    DocumentWsOperation rebase(DocumentWsOperation incoming,
                               List<DocumentOperationEntity> appliedOps,
                               String incomingEditorId,
                               Long incomingClientSeq);

    default DocumentWsOperation rebase(DocumentWsOperation incoming,
                                       List<DocumentOperationEntity> appliedOps,
                                       String incomingEditorId,
                                       Long incomingClientSeq,
                                       RebaseMetadata metadata) {
        return rebase(incoming, appliedOps, incomingEditorId, incomingClientSeq);
    }

    record RebaseMetadata(Long clientClock, Map<String, Long> baseVector) {
    }
}

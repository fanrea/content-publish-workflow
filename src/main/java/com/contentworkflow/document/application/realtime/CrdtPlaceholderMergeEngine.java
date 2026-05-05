package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;

import java.util.List;

/**
 * @deprecated Use {@link CrdtMergeEngine}. This class is kept for compatibility and delegates all behavior.
 */
@Deprecated(forRemoval = false, since = "phase1")
public class CrdtPlaceholderMergeEngine implements MergeEngine {

    private final MergeEngine delegate;

    public CrdtPlaceholderMergeEngine() {
        this(new CrdtMergeEngine());
    }

    CrdtPlaceholderMergeEngine(MergeEngine delegate) {
        this.delegate = delegate;
    }

    @Override
    public String apply(String content, DocumentWsOperation op) {
        return delegate.apply(content, op);
    }

    @Override
    public DocumentWsOperation rebase(DocumentWsOperation incoming,
                                      List<DocumentOperationEntity> appliedOps,
                                      String incomingEditorId,
                                      Long incomingClientSeq) {
        return delegate.rebase(incoming, appliedOps, incomingEditorId, incomingClientSeq);
    }
}

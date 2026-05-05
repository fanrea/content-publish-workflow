package com.contentworkflow.document.application.realtime;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "workflow.realtime", name = "merge-engine", havingValue = "ot", matchIfMissing = true)
public class OtMergeEngine implements MergeEngine {

    @Override
    public String apply(String content, DocumentWsOperation op) {
        return applyTextOperation(content, op);
    }

    static String applyTextOperation(String content, DocumentWsOperation op) {
        int textLength = content.length();
        int position = op.getPosition();
        int length = op.getLength() == null ? 0 : op.getLength();
        String text = op.getText() == null ? "" : op.getText();

        if (position < 0 || position > textLength) {
            throw new BusinessException("DOCUMENT_INVALID_OPERATION", "operation position out of range");
        }
        if (length < 0 || position + length > textLength) {
            throw new BusinessException("DOCUMENT_INVALID_OPERATION", "operation length out of range");
        }

        return switch (op.getOpType()) {
            case INSERT -> content.substring(0, position) + text + content.substring(position);
            case DELETE -> content.substring(0, position) + content.substring(position + length);
            case REPLACE -> content.substring(0, position) + text + content.substring(position + length);
        };
    }

    @Override
    public DocumentWsOperation rebase(DocumentWsOperation incoming,
                                      List<DocumentOperationEntity> appliedOps,
                                      String incomingEditorId,
                                      Long incomingClientSeq) {
        return rebase(incoming, appliedOps, incomingEditorId, incomingClientSeq, null);
    }

    @Override
    public DocumentWsOperation rebase(DocumentWsOperation incoming,
                                      List<DocumentOperationEntity> appliedOps,
                                      String incomingEditorId,
                                      Long incomingClientSeq,
                                      RebaseMetadata metadata) {
        DocumentWsOperation rebased = copyOperation(incoming);
        List<DocumentOperationEntity> relevantAppliedOps = filterRebaseRelevantOps(appliedOps, metadata);
        long incomingOrderingClock = resolveIncomingOrderingClock(incomingEditorId, incomingClientSeq, metadata);
        for (DocumentOperationEntity applied : relevantAppliedOps) {
            if (applied == null || applied.getOpType() == null) {
                continue;
            }
            int appliedPos = safeNonNegative(applied.getOpPosition());
            int appliedLen = safeNonNegative(applied.getOpLength());
            int appliedInsertLen = safeTextLength(applied.getOpText());
            switch (applied.getOpType()) {
                case INSERT -> transformAgainstInsert(
                        rebased,
                        appliedPos,
                        appliedInsertLen,
                        applied.getEditorId(),
                        applied.getClientSeq(),
                        incomingEditorId,
                        incomingOrderingClock
                );
                case DELETE -> transformAgainstDelete(rebased, appliedPos, appliedLen);
                case REPLACE -> {
                    transformAgainstDelete(rebased, appliedPos, appliedLen);
                    transformAgainstInsert(
                            rebased,
                            appliedPos,
                            appliedInsertLen,
                            applied.getEditorId(),
                            applied.getClientSeq(),
                            incomingEditorId,
                            incomingOrderingClock
                    );
                }
            }
        }
        return rebased;
    }

    private void transformAgainstInsert(DocumentWsOperation incoming,
                                        int appliedPos,
                                        int insertedLen,
                                        String appliedEditorId,
                                        Long appliedClientSeq,
                                        String incomingEditorId,
                                        long incomingOrderingClock) {
        if (insertedLen <= 0) {
            return;
        }
        int start = safeNonNegative(incoming.getPosition());
        int length = safeNonNegative(incoming.getLength());
        int end = start + length;

        switch (incoming.getOpType()) {
            case INSERT -> {
                boolean shouldShift = appliedPos < start
                        || (appliedPos == start
                        && shouldShiftOnEqualPosition(
                        appliedEditorId,
                        appliedClientSeq,
                        incomingEditorId,
                        incomingOrderingClock
                ));
                if (shouldShift) {
                    incoming.setPosition(start + insertedLen);
                }
            }
            case DELETE, REPLACE -> {
                if (appliedPos <= start) {
                    start += insertedLen;
                } else if (appliedPos < end) {
                    length += insertedLen;
                }
                incoming.setPosition(start);
                incoming.setLength(length);
            }
        }
    }

    private void transformAgainstDelete(DocumentWsOperation incoming,
                                        int appliedPos,
                                        int deletedLen) {
        if (deletedLen <= 0) {
            return;
        }
        int deleteStart = appliedPos;
        int deleteEnd = appliedPos + deletedLen;
        int start = safeNonNegative(incoming.getPosition());
        int length = safeNonNegative(incoming.getLength());
        int end = start + length;

        switch (incoming.getOpType()) {
            case INSERT -> incoming.setPosition(transformPointByDelete(start, deleteStart, deleteEnd));
            case DELETE, REPLACE -> {
                int newStart = transformPointByDelete(start, deleteStart, deleteEnd);
                int newEnd = transformPointByDelete(end, deleteStart, deleteEnd);
                if (newEnd < newStart) {
                    newEnd = newStart;
                }
                incoming.setPosition(newStart);
                incoming.setLength(newEnd - newStart);
            }
        }
    }

    private int transformPointByDelete(int point, int deleteStart, int deleteEnd) {
        if (point >= deleteEnd) {
            return point - (deleteEnd - deleteStart);
        }
        if (point <= deleteStart) {
            return point;
        }
        return deleteStart;
    }

    private boolean shouldShiftOnEqualPosition(String appliedEditorId,
                                               Long appliedClientSeq,
                                               String incomingEditorId,
                                               long incomingOrderingClock) {
        int editorCmp = safeString(appliedEditorId).compareTo(safeString(incomingEditorId));
        if (editorCmp < 0) {
            return true;
        }
        if (editorCmp > 0) {
            return false;
        }
        return safeLong(appliedClientSeq) < Math.max(0L, incomingOrderingClock);
    }

    private List<DocumentOperationEntity> filterRebaseRelevantOps(List<DocumentOperationEntity> appliedOps,
                                                                   RebaseMetadata metadata) {
        if (appliedOps == null || appliedOps.isEmpty()) {
            return List.of();
        }
        Map<String, Long> normalizedBaseVector = normalizeBaseVector(metadata);
        if (normalizedBaseVector.isEmpty()) {
            return appliedOps;
        }
        List<DocumentOperationEntity> filtered = new ArrayList<>(appliedOps.size());
        for (DocumentOperationEntity appliedOp : appliedOps) {
            if (appliedOp == null) {
                continue;
            }
            String actorId = normalizeActorId(appliedOp.getEditorId());
            Long knownClock = normalizedBaseVector.get(actorId);
            long operationClock = safeLong(appliedOp.getClientSeq());
            if (knownClock != null && operationClock > 0L && operationClock <= knownClock) {
                continue;
            }
            filtered.add(appliedOp);
        }
        return filtered;
    }

    private long resolveIncomingOrderingClock(String incomingEditorId, Long incomingClientSeq, RebaseMetadata metadata) {
        long orderingClock = safeLong(incomingClientSeq);
        if (metadata != null) {
            orderingClock = Math.max(orderingClock, safeLong(metadata.clientClock()));
            if (metadata.baseVector() != null) {
                long floor = Math.max(0L, metadata.baseVector().getOrDefault(normalizeActorId(incomingEditorId), 0L));
                if (orderingClock <= floor) {
                    orderingClock = floor + 1L;
                }
            }
        }
        return orderingClock;
    }

    private Map<String, Long> normalizeBaseVector(RebaseMetadata metadata) {
        if (metadata == null || metadata.baseVector() == null || metadata.baseVector().isEmpty()) {
            return Map.of();
        }
        Map<String, Long> normalized = new java.util.HashMap<>();
        for (Map.Entry<String, Long> entry : metadata.baseVector().entrySet()) {
            String actorId = normalizeActorId(entry.getKey());
            long clock = Math.max(0L, safeLong(entry.getValue()));
            if (actorId.isEmpty()) {
                continue;
            }
            Long previous = normalized.get(actorId);
            if (previous == null || clock > previous) {
                normalized.put(actorId, clock);
            }
        }
        return normalized;
    }

    private String normalizeActorId(String actorId) {
        return actorId == null ? "" : actorId.trim();
    }

    private int safeNonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private int safeTextLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private DocumentWsOperation copyOperation(DocumentWsOperation source) {
        DocumentWsOperation copy = new DocumentWsOperation();
        copy.setOpType(source.getOpType());
        copy.setPosition(source.getPosition());
        copy.setLength(source.getLength());
        copy.setText(source.getText());
        return copy;
    }
}

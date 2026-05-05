package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.application.realtime.crdt.CrdtAtom;
import com.contentworkflow.document.application.realtime.crdt.CrdtCharId;
import com.contentworkflow.document.application.realtime.crdt.CrdtTextState;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "workflow.realtime", name = "merge-engine", havingValue = "crdt")
public class CrdtMergeEngine implements MergeEngine {

    private static final String APPLY_ACTOR_ID = "__crdt_apply__";

    @Override
    public String apply(String content, DocumentWsOperation op) {
        CrdtTextState state = CrdtTextState.fromPlainText(content);
        state.apply(op, APPLY_ACTOR_ID, 0L);
        return state.exportText();
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
        if (relevantAppliedOps.isEmpty()) {
            return rebased;
        }
        int incomingPosition = safeNonNegative(rebased.getPosition());
        int incomingLength = safeNonNegative(rebased.getLength());
        int baseLength = estimateBaseLength(incomingPosition, incomingLength, relevantAppliedOps);
        ReplayContext context = replayContext(baseLength, relevantAppliedOps);
        long incomingClock = resolveIncomingClock(incomingEditorId, incomingClientSeq, metadata);

        int mappedStart = mapPointToReplayedBaseline(incomingPosition, context);
        int mappedEnd = mapPointToReplayedBaseline(incomingPosition + incomingLength, context);
        if (mappedEnd < mappedStart) {
            mappedEnd = mappedStart;
        }

        rebased.setPosition(mappedStart);
        if (rebased.getOpType() == DocumentOpType.INSERT) {
            int stableInsertPosition = orderConcurrentInsertsOnBoundary(
                    mappedStart,
                    incomingPosition,
                    incomingEditorId,
                    incomingClock,
                    context
            );
            rebased.setPosition(stableInsertPosition);
            rebased.setLength(0);
        } else {
            rebased.setLength(mappedEnd - mappedStart);
        }
        return rebased;
    }

    private ReplayContext replayContext(int baseLength, List<DocumentOperationEntity> appliedOps) {
        CrdtTextState state = CrdtTextState.fromPlainText("x".repeat(Math.max(0, baseLength)));
        List<CrdtCharId> seedIds = new ArrayList<>();
        for (CrdtAtom atom : state.atoms()) {
            seedIds.add(atom.getId());
        }
        for (DocumentOperationEntity appliedOp : appliedOps) {
            if (appliedOp == null || appliedOp.getOpType() == null) {
                continue;
            }
            state.apply(
                    toWsOperation(appliedOp),
                    safeString(appliedOp.getEditorId()),
                    safeLong(appliedOp.getClientSeq())
            );
        }
        return new ReplayContext(state, seedIds, new HashSet<>(seedIds));
    }

    private DocumentWsOperation toWsOperation(DocumentOperationEntity appliedOp) {
        DocumentWsOperation operation = new DocumentWsOperation();
        operation.setOpType(appliedOp.getOpType());
        operation.setPosition(safeNonNegative(appliedOp.getOpPosition()));
        operation.setLength(safeNonNegative(appliedOp.getOpLength()));
        operation.setText(appliedOp.getOpText());
        return operation;
    }

    private int mapPointToReplayedBaseline(int pointOnStaleBase, ReplayContext context) {
        if (pointOnStaleBase <= 0) {
            return 0;
        }
        int normalizedPoint = Math.min(pointOnStaleBase, context.seedIds.size());
        List<CrdtAtom> visibleAtoms = visibleAtoms(context.state);
        Map<CrdtCharId, Integer> visibleIndexById = visibleIndexById(visibleAtoms);

        CrdtCharId rightBoundary = firstVisibleSeedFrom(normalizedPoint, context.seedIds, visibleIndexById);
        if (rightBoundary != null) {
            return visibleIndexById.get(rightBoundary);
        }
        CrdtCharId leftBoundary = firstVisibleSeedBackward(normalizedPoint - 1, context.seedIds, visibleIndexById);
        if (leftBoundary != null) {
            return visibleIndexById.get(leftBoundary) + 1;
        }
        return Math.min(normalizedPoint, visibleAtoms.size());
    }

    private int orderConcurrentInsertsOnBoundary(int mappedPosition,
                                                 int incomingOriginalPosition,
                                                 String incomingEditorId,
                                                 long incomingClock,
                                                 ReplayContext context) {
        int originalPoint = Math.max(0, Math.min(incomingOriginalPosition, context.seedIds.size()));
        CrdtCharId leftSeed = originalPoint > 0 ? context.seedIds.get(originalPoint - 1) : null;
        CrdtCharId rightSeed = originalPoint < context.seedIds.size() ? context.seedIds.get(originalPoint) : null;
        int leftAtomIndex = leftSeed == null ? -1 : context.state.atomIndexOf(leftSeed);
        int rightAtomIndex = rightSeed == null ? context.state.atoms().size() : context.state.atomIndexOf(rightSeed);
        if (leftSeed != null && leftAtomIndex < 0) {
            return mappedPosition;
        }
        if (rightSeed != null && rightAtomIndex < 0) {
            return mappedPosition;
        }
        if (rightAtomIndex < leftAtomIndex + 1) {
            return mappedPosition;
        }
        int baseVisiblePosition = context.state.visiblePositionBeforeAtomIndex(leftAtomIndex + 1);
        CrdtCharId incomingId = CrdtCharId.of(safeString(incomingEditorId), incomingClock);
        int shouldBeBefore = 0;
        List<CrdtAtom> allAtoms = context.state.atoms();
        for (int i = leftAtomIndex + 1; i < rightAtomIndex; i++) {
            CrdtAtom atom = allAtoms.get(i);
            if (atom.isTombstone()) {
                continue;
            }
            if (context.seedIdSet().contains(atom.getId())) {
                continue;
            }
            if (atom.getId().compareTo(incomingId) < 0) {
                shouldBeBefore++;
            }
        }
        return baseVisiblePosition + shouldBeBefore;
    }

    private CrdtCharId firstVisibleSeedFrom(int startClockIndex,
                                            List<CrdtCharId> seedIds,
                                            Map<CrdtCharId, Integer> visibleIndexById) {
        for (int index = Math.max(0, startClockIndex); index < seedIds.size(); index++) {
            CrdtCharId id = seedIds.get(index);
            if (visibleIndexById.containsKey(id)) {
                return id;
            }
        }
        return null;
    }

    private CrdtCharId firstVisibleSeedBackward(int startClockIndex,
                                                List<CrdtCharId> seedIds,
                                                Map<CrdtCharId, Integer> visibleIndexById) {
        int from = Math.min(startClockIndex, seedIds.size() - 1);
        for (int index = from; index >= 0; index--) {
            CrdtCharId id = seedIds.get(index);
            if (visibleIndexById.containsKey(id)) {
                return id;
            }
        }
        return null;
    }

    private Map<CrdtCharId, Integer> visibleIndexById(List<CrdtAtom> visibleAtoms) {
        Map<CrdtCharId, Integer> indexById = new HashMap<>();
        for (int i = 0; i < visibleAtoms.size(); i++) {
            indexById.put(visibleAtoms.get(i).getId(), i);
        }
        return indexById;
    }

    private List<CrdtAtom> visibleAtoms(CrdtTextState state) {
        return state.visibleAtoms();
    }

    private int estimateBaseLength(int incomingPosition,
                                   int incomingLength,
                                   List<DocumentOperationEntity> appliedOps) {
        int maxPoint = incomingPosition + incomingLength;
        for (DocumentOperationEntity applied : appliedOps) {
            if (applied == null || applied.getOpType() == null) {
                continue;
            }
            int pos = safeNonNegative(applied.getOpPosition());
            int len = safeNonNegative(applied.getOpLength());
            int point = switch (applied.getOpType()) {
                case INSERT -> pos;
                case DELETE, REPLACE -> pos + len;
            };
            if (point > maxPoint) {
                maxPoint = point;
            }
        }
        return Math.max(0, maxPoint);
    }

    private int safeNonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
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
            if (isAcknowledgedByBaseVector(appliedOp, normalizedBaseVector)) {
                continue;
            }
            filtered.add(appliedOp);
        }
        return filtered;
    }

    private boolean isAcknowledgedByBaseVector(DocumentOperationEntity appliedOp, Map<String, Long> baseVector) {
        String actorId = normalizeActorId(appliedOp.getEditorId());
        Long knownClock = baseVector.get(actorId);
        if (knownClock == null) {
            return false;
        }
        long operationClock = normalizeClock(appliedOp.getClientSeq());
        return operationClock > 0L && operationClock <= knownClock;
    }

    private long resolveIncomingClock(String incomingEditorId, Long incomingClientSeq, RebaseMetadata metadata) {
        String actorId = normalizeActorId(incomingEditorId);
        long fromClientSeq = normalizeClock(incomingClientSeq);
        long fromClientClock = metadata == null ? 0L : normalizeClock(metadata.clientClock());
        long resolved = Math.max(fromClientSeq, fromClientClock);
        long floor = 0L;
        Map<String, Long> normalizedBaseVector = normalizeBaseVector(metadata);
        if (!normalizedBaseVector.isEmpty()) {
            floor = Math.max(0L, normalizedBaseVector.getOrDefault(actorId, 0L));
        }
        if (resolved <= floor) {
            resolved = floor + 1L;
        }
        return resolved;
    }

    private Map<String, Long> normalizeBaseVector(RebaseMetadata metadata) {
        if (metadata == null || metadata.baseVector() == null || metadata.baseVector().isEmpty()) {
            return Map.of();
        }
        Map<String, Long> normalized = new HashMap<>();
        for (Map.Entry<String, Long> entry : metadata.baseVector().entrySet()) {
            String actorId = normalizeActorId(entry.getKey());
            long clock = normalizeClock(entry.getValue());
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

    private long normalizeClock(Long clock) {
        return clock == null ? 0L : Math.max(0L, clock);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private DocumentWsOperation copyOperation(DocumentWsOperation source) {
        Objects.requireNonNull(source, "source must not be null");
        DocumentWsOperation copy = new DocumentWsOperation();
        copy.setOpType(source.getOpType());
        copy.setPosition(source.getPosition());
        copy.setLength(source.getLength());
        copy.setText(source.getText());
        return copy;
    }

    private record ReplayContext(CrdtTextState state, List<CrdtCharId> seedIds, Set<CrdtCharId> seedIdSet) {
        private ReplayContext {
            Objects.requireNonNull(state, "state must not be null");
            Objects.requireNonNull(seedIds, "seedIds must not be null");
            Objects.requireNonNull(seedIdSet, "seedIdSet must not be null");
        }
    }
}

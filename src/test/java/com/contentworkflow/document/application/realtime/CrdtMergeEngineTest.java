package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CrdtMergeEngineTest {

    private final CrdtMergeEngine mergeEngine = new CrdtMergeEngine();

    @Test
    void apply_shouldApplyTextOperation() {
        DocumentWsOperation op = operation(DocumentOpType.REPLACE, 1, 2, "XYZ");

        String merged = mergeEngine.apply("abcdef", op);

        assertThat(merged).isEqualTo("aXYZdef");
    }

    @Test
    void rebase_shouldReturnCopyWhenNoAppliedOps() {
        DocumentWsOperation incoming = operation(DocumentOpType.INSERT, 2, 0, "A");

        DocumentWsOperation rebased = mergeEngine.rebase(incoming, List.of(), "editor-1", 10L);

        assertThat(rebased).isNotSameAs(incoming);
        assertThat(rebased.getOpType()).isEqualTo(incoming.getOpType());
        assertThat(rebased.getPosition()).isEqualTo(incoming.getPosition());
        assertThat(rebased.getLength()).isEqualTo(incoming.getLength());
        assertThat(rebased.getText()).isEqualTo(incoming.getText());
    }

    @Test
    void rebase_shouldHandleStaleBaseInsertWithDeterministicTieBreak() {
        DocumentWsOperation incoming = operation(DocumentOpType.INSERT, 1, 0, "b");
        DocumentOperationEntity applied = persistedOp(DocumentOpType.INSERT, 1, 0, "a", "editor-a", 1L);

        DocumentWsOperation rebased = mergeEngine.rebase(incoming, List.of(applied), "editor-b", 1L);

        assertThat(rebased.getPosition()).isEqualTo(2);
        assertThat(rebased.getText()).isEqualTo("b");
    }

    @Test
    void rebase_shouldHandleStaleBaseDeleteByAppliedInsertReplayOrder() {
        DocumentWsOperation incoming = operation(DocumentOpType.DELETE, 1, 3, null);
        DocumentOperationEntity applied = persistedOp(DocumentOpType.INSERT, 2, 0, "XX", "editor-a", 1L);

        DocumentWsOperation rebased = mergeEngine.rebase(incoming, List.of(applied), "editor-b", 5L);

        assertThat(rebased.getPosition()).isEqualTo(1);
        assertThat(rebased.getLength()).isEqualTo(5);
    }

    @Test
    void rebase_shouldHandleStaleBaseReplaceAgainstAppliedDelete() {
        DocumentWsOperation incoming = operation(DocumentOpType.REPLACE, 4, 3, "Z");
        DocumentOperationEntity applied = persistedOp(DocumentOpType.DELETE, 1, 2, null, "editor-a", 7L);

        DocumentWsOperation rebased = mergeEngine.rebase(incoming, List.of(applied), "editor-r", 9L);

        assertThat(rebased.getPosition()).isEqualTo(2);
        assertThat(rebased.getLength()).isEqualTo(3);
        assertThat(rebased.getText()).isEqualTo("Z");
    }

    @Test
    void rebase_shouldReplayAppliedOpsInGivenOrder() {
        DocumentWsOperation incoming = operation(DocumentOpType.DELETE, 1, 2, null);
        DocumentOperationEntity appliedInsert = persistedOp(DocumentOpType.INSERT, 2, 0, "XX", "editor-a", 2L);
        DocumentOperationEntity appliedDelete = persistedOp(DocumentOpType.DELETE, 0, 1, null, "editor-b", 3L);

        DocumentWsOperation rebased = mergeEngine.rebase(
                incoming,
                List.of(appliedInsert, appliedDelete),
                "editor-c",
                10L
        );

        assertThat(rebased.getPosition()).isEqualTo(0);
        assertThat(rebased.getLength()).isEqualTo(4);
    }

    @Test
    void rebase_shouldConvergeForConcurrentSamePositionInsertions() {
        String base = "XY";
        DocumentOperationEntity opA = persistedOp(DocumentOpType.INSERT, 1, 0, "a", "editor-a", 1L);
        DocumentOperationEntity opB = persistedOp(DocumentOpType.INSERT, 1, 0, "b", "editor-b", 1L);

        DocumentWsOperation bRebasedAgainstA = mergeEngine.rebase(operation(DocumentOpType.INSERT, 1, 0, "b"), List.of(opA), "editor-b", 1L);
        String resultAThenB = mergeEngine.apply(mergeEngine.apply(base, asIncoming(opA)), bRebasedAgainstA);

        DocumentWsOperation aRebasedAgainstB = mergeEngine.rebase(operation(DocumentOpType.INSERT, 1, 0, "a"), List.of(opB), "editor-a", 1L);
        String resultBThenA = mergeEngine.apply(mergeEngine.apply(base, asIncoming(opB)), aRebasedAgainstB);

        assertThat(resultAThenB).isEqualTo("XabY");
        assertThat(resultBThenA).isEqualTo("XabY");
    }

    @Test
    void rebase_shouldProduceDeterministicServerOrderAfterMixedDeleteAndInsertReplay() {
        String base = "abcdef";
        DocumentOperationEntity deleteOp = persistedOp(DocumentOpType.DELETE, 1, 3, null, "editor-a", 1L);
        DocumentOperationEntity insertOp = persistedOp(DocumentOpType.INSERT, 2, 0, "XY", "editor-b", 1L);
        DocumentOperationEntity incomingEntity = persistedOp(DocumentOpType.REPLACE, 3, 2, "Q", "editor-c", 2L);

        DocumentWsOperation incoming = asIncoming(incomingEntity);
        DocumentWsOperation rebased = mergeEngine.rebase(
                incoming,
                List.of(deleteOp, insertOp),
                "editor-c",
                2L
        );

        String rebasedResult = mergeEngine.apply(
                mergeEngine.apply(mergeEngine.apply(base, asIncoming(deleteOp)), asIncoming(insertOp)),
                rebased
        );

        assertThat(rebasedResult).isEqualTo("aQXYf");
    }

    @Test
    void rebase_shouldConvergeForThreeWayConcurrentSamePositionInsertions() {
        String base = "MN";
        DocumentOperationEntity opA = persistedOp(DocumentOpType.INSERT, 1, 0, "a", "editor-a", 1L);
        DocumentOperationEntity opB = persistedOp(DocumentOpType.INSERT, 1, 0, "b", "editor-b", 1L);
        DocumentOperationEntity opC = persistedOp(DocumentOpType.INSERT, 1, 0, "c", "editor-c", 1L);

        String orderABThenC = mergeEngine.apply(
                mergeEngine.apply(
                        mergeEngine.apply(base, asIncoming(opA)),
                        mergeEngine.rebase(asIncoming(opB), List.of(opA), "editor-b", 1L)
                ),
                mergeEngine.rebase(asIncoming(opC), List.of(opA, opB), "editor-c", 1L)
        );

        String orderCAThenB = mergeEngine.apply(
                mergeEngine.apply(
                        mergeEngine.apply(base, asIncoming(opC)),
                        mergeEngine.rebase(asIncoming(opA), List.of(opC), "editor-a", 1L)
                ),
                mergeEngine.rebase(asIncoming(opB), List.of(opC, opA), "editor-b", 1L)
        );

        assertThat(orderABThenC).isEqualTo("MabcN");
        assertThat(orderCAThenB).isEqualTo("MabcN");
    }

    @Test
    void rebase_shouldOrderBoundaryInsertsWhenRightSeedIsTombstoned() {
        DocumentWsOperation incoming = operation(DocumentOpType.INSERT, 1, 0, "z");
        DocumentOperationEntity appliedInsert = persistedOp(DocumentOpType.INSERT, 1, 0, "y", "editor-y", 2L);
        DocumentOperationEntity appliedDeleteRightSeed = persistedOp(DocumentOpType.DELETE, 2, 1, null, "editor-d", 3L);

        DocumentWsOperation rebased = mergeEngine.rebase(
                incoming,
                List.of(appliedInsert, appliedDeleteRightSeed),
                "editor-z",
                5L
        );

        assertThat(rebased.getPosition()).isEqualTo(2);
    }

    @Test
    void rebase_shouldKeepLowerClockInsertBeforeExistingBoundaryInserts() {
        DocumentWsOperation incoming = operation(DocumentOpType.INSERT, 1, 0, "x");
        DocumentOperationEntity appliedInsert = persistedOp(DocumentOpType.INSERT, 1, 0, "y", "editor-y", 7L);
        DocumentOperationEntity appliedDeleteRightSeed = persistedOp(DocumentOpType.DELETE, 2, 1, null, "editor-d", 8L);

        DocumentWsOperation rebased = mergeEngine.rebase(
                incoming,
                List.of(appliedInsert, appliedDeleteRightSeed),
                "editor-x",
                1L
        );

        assertThat(rebased.getPosition()).isEqualTo(1);
    }

    @Test
    void rebase_shouldMapReplaceRangeAgainstServerOrderedPrefixDelete() {
        DocumentWsOperation incoming = operation(DocumentOpType.REPLACE, 1, 5, "Q");
        DocumentOperationEntity appliedDeleteAll = persistedOp(DocumentOpType.DELETE, 0, 3, null, "editor-a", 1L);

        DocumentWsOperation rebased = mergeEngine.rebase(incoming, List.of(appliedDeleteAll), "editor-r", 3L);

        assertThat(rebased.getPosition()).isEqualTo(0);
        assertThat(rebased.getLength()).isEqualTo(3);
    }

    @Test
    void rebase_shouldHandleInterleavedDeletesAgainstSameSpan() {
        String base = "abcdef";
        DocumentOperationEntity appliedDeleteLeft = persistedOp(DocumentOpType.DELETE, 1, 2, null, "editor-a", 1L);
        DocumentOperationEntity appliedDeleteRight = persistedOp(DocumentOpType.DELETE, 3, 2, null, "editor-b", 1L);
        DocumentWsOperation incomingDelete = operation(DocumentOpType.DELETE, 2, 3, null);

        DocumentWsOperation rebased = mergeEngine.rebase(
                incomingDelete,
                List.of(appliedDeleteLeft, appliedDeleteRight),
                "editor-c",
                1L
        );

        String result = mergeEngine.apply(
                mergeEngine.apply(
                        mergeEngine.apply(base, asIncoming(appliedDeleteLeft)),
                        asIncoming(appliedDeleteRight)
                ),
                rebased
        );

        assertThat(result).isEqualTo("a");
    }

    @Test
    void rebase_shouldSkipAppliedOpsAlreadyAcknowledgedByBaseVector() {
        DocumentWsOperation incoming = operation(DocumentOpType.INSERT, 1, 0, "b");
        DocumentOperationEntity alreadySeen = persistedOp(DocumentOpType.INSERT, 1, 0, "a", "editor-a", 3L);

        DocumentWsOperation rebased = mergeEngine.rebase(
                incoming,
                List.of(alreadySeen),
                "editor-b",
                2L,
                new MergeEngine.RebaseMetadata(2L, Map.of("editor-a", 3L))
        );

        assertThat(rebased.getPosition()).isEqualTo(1);
        assertThat(rebased.getText()).isEqualTo("b");
    }

    @Test
    void rebase_shouldUseClientClockWhenOrderingConcurrentSameActorInsertions() {
        DocumentWsOperation incoming = operation(DocumentOpType.INSERT, 1, 0, "b");
        DocumentOperationEntity applied = persistedOp(DocumentOpType.INSERT, 1, 0, "a", "editor-z", 5L);

        DocumentWsOperation withoutClientClock = mergeEngine.rebase(
                incoming,
                List.of(applied),
                "editor-z",
                0L,
                new MergeEngine.RebaseMetadata(null, Map.of())
        );
        DocumentWsOperation withClientClock = mergeEngine.rebase(
                incoming,
                List.of(applied),
                "editor-z",
                0L,
                new MergeEngine.RebaseMetadata(10L, Map.of())
        );

        assertThat(withoutClientClock.getPosition()).isEqualTo(1);
        assertThat(withClientClock.getPosition()).isEqualTo(2);
    }

    private DocumentWsOperation operation(DocumentOpType type, int position, int length, String text) {
        DocumentWsOperation operation = new DocumentWsOperation();
        operation.setOpType(type);
        operation.setPosition(position);
        operation.setLength(length);
        operation.setText(text);
        return operation;
    }

    private DocumentOperationEntity persistedOp(DocumentOpType type,
                                                int position,
                                                int length,
                                                String text,
                                                String editorId,
                                                Long clientSeq) {
        DocumentOperationEntity entity = new DocumentOperationEntity();
        entity.setOpType(type);
        entity.setOpPosition(position);
        entity.setOpLength(length);
        entity.setOpText(text);
        entity.setEditorId(editorId);
        entity.setClientSeq(clientSeq);
        return entity;
    }

    private DocumentWsOperation asIncoming(DocumentOperationEntity applied) {
        return operation(
                applied.getOpType(),
                applied.getOpPosition() == null ? 0 : applied.getOpPosition(),
                applied.getOpLength() == null ? 0 : applied.getOpLength(),
                applied.getOpText()
        );
    }

    private DocumentOperationEntity persistedFromIncoming(DocumentWsOperation operation,
                                                          String editorId,
                                                          Long clientSeq) {
        return persistedOp(
                operation.getOpType(),
                operation.getPosition() == null ? 0 : operation.getPosition(),
                operation.getLength() == null ? 0 : operation.getLength(),
                operation.getText(),
                editorId,
                clientSeq
        );
    }
}

package com.contentworkflow.document.application.realtime.crdt;

import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CrdtTextStateTest {

    @Test
    void delete_shouldKeepTombstonesButExportWithoutDeletedChars() {
        CrdtTextState state = CrdtTextState.fromPlainText("abcd");
        state.apply(operation(DocumentOpType.DELETE, 1, 2, null), "actor-a", 10L);

        assertThat(state.exportText()).isEqualTo("ad");
        long tombstoneCount = state.atoms().stream().filter(CrdtAtom::isTombstone).count();
        assertThat(tombstoneCount).isEqualTo(2L);
        assertThat(state.atoms()).hasSize(4);
    }

    @Test
    void insert_shouldAssignActorClockIds() {
        CrdtTextState state = CrdtTextState.fromPlainText("XY");
        state.apply(operation(DocumentOpType.INSERT, 1, 0, "ab"), "actor-a", 7L);

        assertThat(state.exportText()).isEqualTo("XabY");
        assertThat(state.atoms().stream().filter(atom -> !atom.isTombstone()).map(atom -> atom.getId().actorId()))
                .contains("actor-a");
        assertThat(state.atoms().stream()
                .filter(atom -> "actor-a".equals(atom.getId().actorId()))
                .map(atom -> atom.getId().clock()))
                .containsExactly(7L, 8L);
    }

    @Test
    void helpers_shouldClampVisiblePositionsAndDeleteLength() {
        CrdtTextState state = CrdtTextState.fromPlainText("abcd");
        state.apply(operation(DocumentOpType.DELETE, 1, 2, null), "actor-a", 1L);

        assertThat(state.visibleLength()).isEqualTo(2);
        assertThat(state.clampVisiblePosition(-10)).isEqualTo(0);
        assertThat(state.clampVisiblePosition(100)).isEqualTo(2);
        assertThat(state.resolveDeleteLength(1, 10)).isEqualTo(1);
        assertThat(state.resolveDeleteLength(5, 3)).isEqualTo(0);
    }

    @Test
    void visiblePositionBeforeAtomIndex_shouldIgnoreTombstones() {
        CrdtTextState state = CrdtTextState.fromPlainText("ab");
        state.apply(operation(DocumentOpType.INSERT, 1, 0, "X"), "actor-x", 5L);
        state.apply(operation(DocumentOpType.DELETE, 2, 1, null), "actor-d", 1L);

        int indexOfB = state.atoms().stream()
                .filter(atom -> atom.getValue() == 'b')
                .findFirst()
                .map(atom -> state.atomIndexOf(atom.getId()))
                .orElseThrow();

        assertThat(state.visiblePositionBeforeAtomIndex(indexOfB)).isEqualTo(2);
        assertThat(state.visibleAtoms().stream().map(CrdtAtom::getValue)).containsExactly('a', 'X');
    }

    @Test
    void compactTombstones_shouldRemoveDeletedAtomsOnly() {
        CrdtTextState state = CrdtTextState.fromPlainText("abcd");
        state.apply(operation(DocumentOpType.DELETE, 1, 2, null), "actor-a", 1L);

        int removed = state.compactTombstones();

        assertThat(removed).isEqualTo(2);
        assertThat(state.atoms()).hasSize(2);
        assertThat(state.exportText()).isEqualTo("ad");
    }

    @Test
    void apply_shouldHandleEmptyTextAndOutOfRangeReplace() {
        CrdtTextState state = CrdtTextState.fromPlainText("");
        state.apply(operation(DocumentOpType.INSERT, 0, 0, ""), "actor-a", 1L);
        state.apply(operation(DocumentOpType.REPLACE, 100, 5, "Z"), "actor-a", 2L);

        assertThat(state.exportText()).isEqualTo("Z");
        assertThat(state.visibleLength()).isEqualTo(1);
    }

    @Test
    void fromSnapshot_shouldReconcileActorClockWithExistingAtoms() {
        CrdtTextState state = CrdtTextState.fromSnapshot(
                List.of(new CrdtAtom(new CrdtCharId("actor-a", 5L), 'X', false)),
                Map.of("actor-a", 1L)
        );

        state.apply(operation(DocumentOpType.INSERT, 1, 0, "Y"), "actor-a", 2L);

        assertThat(state.exportText()).isEqualTo("XY");
        long insertedClock = state.atoms().stream()
                .filter(atom -> atom.getValue() == 'Y')
                .findFirst()
                .orElseThrow()
                .getId()
                .clock();
        assertThat(insertedClock).isEqualTo(6L);
    }

    private DocumentWsOperation operation(DocumentOpType type, int position, int length, String text) {
        DocumentWsOperation op = new DocumentWsOperation();
        op.setOpType(type);
        op.setPosition(position);
        op.setLength(length);
        op.setText(text);
        return op;
    }
}

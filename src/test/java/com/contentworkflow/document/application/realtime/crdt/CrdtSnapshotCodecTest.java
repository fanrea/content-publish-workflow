package com.contentworkflow.document.application.realtime.crdt;

import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrdtSnapshotCodecTest {

    private final CrdtSnapshotCodec codec = new CrdtSnapshotCodec();

    @Test
    void encodeDecode_roundTripShouldKeepExportText() {
        CrdtTextState state = CrdtTextState.fromPlainText("hello");
        state.apply(operation(DocumentOpType.REPLACE, 1, 3, "i"), "actor-a", 10L);

        String encoded = codec.encode(state);
        CrdtTextState decoded = codec.decode(encoded);

        assertThat(encoded).startsWith("crdt-v1:");
        assertThat(decoded.exportText()).isEqualTo(state.exportText());
    }

    @Test
    void encodeDecode_shouldPreserveTombstones() {
        CrdtTextState state = CrdtTextState.fromPlainText("abcd");
        state.apply(operation(DocumentOpType.DELETE, 1, 2, null), "actor-a", 1L);

        CrdtTextState decoded = codec.decode(codec.encode(state));

        long sourceTombstones = state.atoms().stream().filter(CrdtAtom::isTombstone).count();
        long decodedTombstones = decoded.atoms().stream().filter(CrdtAtom::isTombstone).count();
        assertThat(decoded.exportText()).isEqualTo("ad");
        assertThat(decodedTombstones).isEqualTo(sourceTombstones);
    }

    @Test
    void decode_shouldThrowClearExceptionForInvalidPayload() {
        assertThatThrownBy(() -> codec.decode("v0:{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported crdt snapshot version prefix");

        assertThatThrownBy(() -> codec.decode("crdt-v1:{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid crdt snapshot payload");

        assertThatThrownBy(() -> codec.decode("crdt-v1:{\"atoms\":[{\"actorId\":\"a\",\"clock\":1,\"value\":\"xy\",\"tombstone\":false}],\"actorClocks\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("atom value must contain exactly one character");

        assertThatThrownBy(() -> codec.decode("crdt-v1:{\"atoms\":[{\"actorId\":\"a\",\"clock\":1,\"value\":\"x\",\"tombstone\":false},{\"actorId\":\"a\",\"clock\":1,\"value\":\"y\",\"tombstone\":false}],\"actorClocks\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate crdt atom id");
    }

    @Test
    void decodeEncode_shouldNormalizeActorClockToHighestSeenAtomClock() {
        String payload = "crdt-v1:{\"atoms\":[{\"actorId\":\"actor-a\",\"clock\":3,\"value\":\"x\",\"tombstone\":false}],\"actorClocks\":{\"actor-a\":1}}";

        String reEncoded = codec.encode(codec.decode(payload));

        assertThat(reEncoded).contains("\"actor-a\":3");
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

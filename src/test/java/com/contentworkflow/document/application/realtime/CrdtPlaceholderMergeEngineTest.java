package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrdtPlaceholderMergeEngineTest {

    @Test
    void shouldDelegateToCrdtMergeEngineForBackwardCompatibility() {
        CrdtPlaceholderMergeEngine placeholder = new CrdtPlaceholderMergeEngine();

        DocumentWsOperation op = new DocumentWsOperation();
        op.setOpType(DocumentOpType.INSERT);
        op.setPosition(1);
        op.setLength(0);
        op.setText("X");

        assertThat(placeholder.apply("ab", op)).isEqualTo("aXb");
    }
}

package com.contentworkflow.document.application.realtime;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtMergeEngineTest {

    private final OtMergeEngine mergeEngine = new OtMergeEngine();

    @Test
    void apply_shouldInsertText() {
        String merged = mergeEngine.apply("abcdef", operation(DocumentOpType.INSERT, 3, 0, "XYZ"));

        assertThat(merged).isEqualTo("abcXYZdef");
    }

    @Test
    void apply_shouldDeleteText() {
        String merged = mergeEngine.apply("abcdef", operation(DocumentOpType.DELETE, 2, 3, null));

        assertThat(merged).isEqualTo("abf");
    }

    @Test
    void apply_shouldReplaceText() {
        String merged = mergeEngine.apply("abcdef", operation(DocumentOpType.REPLACE, 1, 4, "QQ"));

        assertThat(merged).isEqualTo("aQQf");
    }

    @Test
    void apply_shouldKeepResultStableAcrossContinuousEdits() {
        String content = "0123456789";
        content = mergeEngine.apply(content, operation(DocumentOpType.INSERT, 5, 0, "AAA"));
        content = mergeEngine.apply(content, operation(DocumentOpType.DELETE, 2, 4, null));
        content = mergeEngine.apply(content, operation(DocumentOpType.REPLACE, 3, 2, "Z"));

        assertThat(content).isEqualTo("01AZ6789");
    }

    @Test
    void apply_shouldKeepBoundaryValidationSemantics() {
        assertThatThrownBy(() -> mergeEngine.apply("abc", operation(DocumentOpType.INSERT, 4, 0, "x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getCode()).isEqualTo("DOCUMENT_INVALID_OPERATION");
                    assertThat(business.getMessage()).isEqualTo("operation position out of range");
                });

        assertThatThrownBy(() -> mergeEngine.apply("abc", operation(DocumentOpType.INSERT, 1, 3, "x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getCode()).isEqualTo("DOCUMENT_INVALID_OPERATION");
                    assertThat(business.getMessage()).isEqualTo("operation length out of range");
                });
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

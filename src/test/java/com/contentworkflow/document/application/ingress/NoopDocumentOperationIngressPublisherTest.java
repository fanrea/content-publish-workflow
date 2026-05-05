package com.contentworkflow.document.application.ingress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoopDocumentOperationIngressPublisherTest {

    @Test
    void publish_shouldFailFastWithActionableMessage() {
        NoopDocumentOperationIngressPublisher publisher = new NoopDocumentOperationIngressPublisher();

        assertThatThrownBy(() -> publisher.publish(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EDIT_OP ingress requires RocketMQ")
                .hasMessageContaining("workflow.ingress.rocketmq.enabled=true");
    }
}

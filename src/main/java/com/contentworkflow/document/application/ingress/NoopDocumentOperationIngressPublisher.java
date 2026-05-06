package com.contentworkflow.document.application.ingress;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "workflow.ingress.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDocumentOperationIngressPublisher implements DocumentOperationIngressPublisher {

    @Override
    public void publish(DocumentOperationIngressCommand command) {
        throw new IllegalStateException(
                "EDIT_OP ingress requires RocketMQ. Enable workflow.ingress.rocketmq.enabled=true"
        );
    }
}

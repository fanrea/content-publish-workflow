package com.contentworkflow.document.application.ingress;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(DocumentOperationIngressPublisher.class)
public class NoopDocumentOperationIngressPublisher implements DocumentOperationIngressPublisher {

    @Override
    public void publish(DocumentOperationIngressCommand command) {
        throw new IllegalStateException(
                "EDIT_OP ingress requires RocketMQ. Enable workflow.ingress.rocketmq.enabled=true"
        );
    }
}

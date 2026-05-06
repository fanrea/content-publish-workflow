package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.interfaces.ws.DocumentWsEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "workflow.realtime.redis-broadcast", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDocumentRealtimeCrossGatewayBroadcaster implements DocumentRealtimeCrossGatewayBroadcaster {

    @Override
    public void publish(DocumentWsEvent event) {
        // no-op
    }

    @Override
    public void publishTransient(DocumentWsEvent event) {
        // no-op
    }
}

package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.interfaces.ws.DocumentWsEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(DocumentRealtimeCrossGatewayBroadcaster.class)
public class NoopDocumentRealtimeCrossGatewayBroadcaster implements DocumentRealtimeCrossGatewayBroadcaster {

    @Override
    public void publish(DocumentWsEvent event) {
        // no-op
    }
}

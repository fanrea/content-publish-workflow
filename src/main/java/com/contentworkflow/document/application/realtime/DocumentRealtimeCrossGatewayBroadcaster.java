package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.interfaces.ws.DocumentWsEvent;

/**
 * Cross-gateway broadcast transport abstraction.
 */
public interface DocumentRealtimeCrossGatewayBroadcaster {

    void publish(DocumentWsEvent event);
}

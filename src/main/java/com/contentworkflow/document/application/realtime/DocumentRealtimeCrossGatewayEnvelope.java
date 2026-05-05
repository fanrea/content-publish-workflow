package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.interfaces.ws.DocumentWsEvent;

/**
 * Redis bus payload for realtime cross-gateway fanout.
 */
public record DocumentRealtimeCrossGatewayEnvelope(
        String sourceGatewayId,
        Long documentId,
        DocumentWsEvent event
) {
}

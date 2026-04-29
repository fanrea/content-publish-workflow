package com.contentworkflow.document.application.event;

import java.time.LocalDateTime;
import java.util.Map;

public record DocumentDomainEvent(
        String eventType,
        Long documentId,
        Integer revision,
        String operatorId,
        String operatorName,
        Map<String, Object> payload,
        LocalDateTime eventTime
) {
    public static DocumentDomainEvent of(String eventType,
                                         Long documentId,
                                         Integer revision,
                                         String operatorId,
                                         String operatorName,
                                         Map<String, Object> payload) {
        return new DocumentDomainEvent(
                eventType,
                documentId,
                revision,
                operatorId,
                operatorName,
                payload == null ? Map.of() : payload,
                LocalDateTime.now()
        );
    }
}

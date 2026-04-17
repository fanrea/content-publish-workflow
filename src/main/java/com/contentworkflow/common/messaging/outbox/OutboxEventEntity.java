package com.contentworkflow.common.messaging.outbox;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class OutboxEventEntity {

    private Long id;
    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private Integer aggregateVersion;
    private String exchangeName;
    private String routingKey;
    private String payloadJson;
    private String headersJson;
    private OutboxEventStatus status;
    private int attempt;
    private LocalDateTime nextRetryAt;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private LocalDateTime updatedAt;

    public void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = OutboxEventStatus.NEW;
        }
    }

    public void touchForUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

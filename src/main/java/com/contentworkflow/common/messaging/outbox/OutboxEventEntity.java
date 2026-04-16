package com.contentworkflow.common.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 持久化实体，用于映射数据库记录并承载 ORM 层的字段信息。
 */
@Entity
@Table(
        name = "workflow_outbox_event",
        indexes = {
                @Index(name = "idx_outbox_status_next", columnList = "status,next_retry_at,created_at"),
                @Index(name = "idx_outbox_locked", columnList = "locked_at"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type,aggregate_id")
        }
)
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "aggregate_version")
    private Integer aggregateVersion;

    @Column(name = "exchange_name", nullable = false, length = 128)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 256)
    private String routingKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 处理 pre persist 相关逻辑，并返回对应的执行结果。
     */

    @PrePersist
    public void prePersist() {
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

    /**
     * 处理 pre update 相关逻辑，并返回对应的执行结果。
     */

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 统计值或数量结果
     */

    public Long getId() {
        return id;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getEventId() {
        return eventId;
    }

    /**
     * 处理 set event id 相关逻辑，并返回对应的执行结果。
     *
     * @param eventId 相关业务对象的唯一标识
     */

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getEventType() {
        return eventType;
    }

    /**
     * 处理 set event type 相关逻辑，并返回对应的执行结果。
     *
     * @param eventType 参数 eventType 对应的业务输入值
     */

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 处理 set aggregate type 相关逻辑，并返回对应的执行结果。
     *
     * @param aggregateType 参数 aggregateType 对应的业务输入值
     */

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * 处理 set aggregate id 相关逻辑，并返回对应的执行结果。
     *
     * @param aggregateId 相关业务对象的唯一标识
     */

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 统计值或数量结果
     */

    public Integer getAggregateVersion() {
        return aggregateVersion;
    }

    /**
     * 处理 set aggregate version 相关逻辑，并返回对应的执行结果。
     *
     * @param aggregateVersion 参数 aggregateVersion 对应的业务输入值
     */

    public void setAggregateVersion(Integer aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getExchangeName() {
        return exchangeName;
    }

    /**
     * 处理 set exchange name 相关逻辑，并返回对应的执行结果。
     *
     * @param exchangeName 参数 exchangeName 对应的业务输入值
     */

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * 处理 set routing key 相关逻辑，并返回对应的执行结果。
     *
     * @param routingKey 参数 routingKey 对应的业务输入值
     */

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getPayloadJson() {
        return payloadJson;
    }

    /**
     * 处理 set payload json 相关逻辑，并返回对应的执行结果。
     *
     * @param payloadJson 参数 payloadJson 对应的业务输入值
     */

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getHeadersJson() {
        return headersJson;
    }

    /**
     * 处理 set headers json 相关逻辑，并返回对应的执行结果。
     *
     * @param headersJson 参数 headersJson 对应的业务输入值
     */

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public OutboxEventStatus getStatus() {
        return status;
    }

    /**
     * 处理 set status 相关逻辑，并返回对应的执行结果。
     *
     * @param status 状态值
     */

    public void setStatus(OutboxEventStatus status) {
        this.status = status;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 统计值或数量结果
     */

    public int getAttempt() {
        return attempt;
    }

    /**
     * 处理 set attempt 相关逻辑，并返回对应的执行结果。
     *
     * @param attempt 参数 attempt 对应的业务输入值
     */

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    /**
     * 处理 set next retry at 相关逻辑，并返回对应的执行结果。
     *
     * @param nextRetryAt 时间相关参数
     */

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getLockedBy() {
        return lockedBy;
    }

    /**
     * 处理 set locked by 相关逻辑，并返回对应的执行结果。
     *
     * @param lockedBy 参数 lockedBy 对应的业务输入值
     */

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    /**
     * 处理 set locked at 相关逻辑，并返回对应的执行结果。
     *
     * @param lockedAt 时间相关参数
     */

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 处理 set error message 相关逻辑，并返回对应的执行结果。
     *
     * @param errorMessage 参数 errorMessage 对应的业务输入值
     */

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    /**
     * 处理 set sent at 相关逻辑，并返回对应的执行结果。
     *
     * @param sentAt 时间相关参数
     */

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}


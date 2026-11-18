package com.luckyflow.config.messaging;

import java.util.Objects;

import org.springframework.amqp.rabbit.connection.CorrelationData;

import lombok.Getter;

/**
 * 关联一次 Outbox 发布尝试与 RabbitMQ Confirm/Return 结果。
 * processingToken 同时作为 CorrelationData.id，确保迟到回调能够识别原发布尝试。
 */
@Getter 
public final class OutboxCorrelationData extends CorrelationData {

    private final Long outboxId;
    private final String processingToken;
    private final int retryCount;

    public OutboxCorrelationData(Long outboxId, String processingToken, int retryCount) {
        super(processingToken);
        this.outboxId = Objects.requireNonNull(outboxId, "outboxId must not be null");
        this.processingToken = processingToken;
        this.retryCount = retryCount;
    }
}

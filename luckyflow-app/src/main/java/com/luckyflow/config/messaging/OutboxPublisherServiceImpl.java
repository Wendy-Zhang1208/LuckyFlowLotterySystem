package com.luckyflow.config.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.luckyflow.lottery.constant.OutboxEventTypeConstants;
import com.luckyflow.lottery.entity.OutboxEvent;
import com.luckyflow.lottery.service.Interface.OutboxClaimService;
import com.luckyflow.lottery.service.Interface.OutboxStateService;

/**
 * 在数据库 claim 事务提交后，将 Outbox 事件异步发布到 RabbitMQ。
 *
 * <p>本服务不持有数据库事务，也不等待 Confirm；长期没有明确结果的 attempt
 * 保持 PROCESSING，由现有 Recovery 统一收敛。</p>
 */
@Service
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisherServiceImpl.class);

    private final OutboxClaimService outboxClaimService;
    private final OutboxStateService outboxStateService;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisherServiceImpl(
            OutboxClaimService outboxClaimService,
            OutboxStateService outboxStateService,
            RabbitTemplate rabbitTemplate
    ) {
        this.outboxClaimService = outboxClaimService;
        this.outboxStateService = outboxStateService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * claim 一批事件并逐条发起异步发布。
     *
     * @return 本次成功 claim 的事件数量，不代表 Broker 已经 Confirm 成功
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int publishBatch(int batchSize) {
        List<OutboxEvent> events = outboxClaimService.claimBatch(batchSize);
        for (OutboxEvent event : events) {
            try {
                publishClaimedEvent(event);
            } catch (RuntimeException e) {
                LOGGER.error(
                        "Unexpected invariant violation while publishing outbox event, outboxId={}",
                        event == null ? null : event.getId(),
                        e
                );
            }
        }
        return events.size();
    }

    private void publishClaimedEvent(OutboxEvent event) {
        OutboxCorrelationData correlationData = buildCorrelationData(event);
        Message message;
        try {
            message = buildMessage(event);
        } catch (RuntimeException preparationFailure) {
            // Message 尚未进入 RabbitTemplate，可以确定 Broker 没有收到，允许立即失败回退。
            markPublishFailed(
                    correlationData,
                    describeLocalPreparationFailure(preparationFailure)
            );
            return;
        }

        // 必须先注册结果处理器再发送，避免极快 Confirm 先于业务回调注册完成。
        correlationData.getFuture().whenComplete(
                (confirm, completionFailure) ->
                        handleConfirm(correlationData, confirm, completionFailure)
        );

        try {
            LOGGER.debug(
                    "RabbitMQ publish send started, outboxId={}, processingToken={}",
                    correlationData.getOutboxId(),
                    correlationData.getProcessingToken()
            );
            rabbitTemplate.send(
                    LotteryEventRabbitTopology.EXCHANGE,
                    LotteryEventRabbitTopology.SUCCESS_ROUTING_KEY,
                    message,
                    correlationData
            );
            LOGGER.debug(
                    "RabbitMQ publish send returned, outboxId={}, processingToken={}",
                    correlationData.getOutboxId(),
                    correlationData.getProcessingToken()
            );
        } catch (RuntimeException publishFailure) {
            // 一旦进入 send，连接、Channel 或 I/O 异常都不能证明 Broker 一定未收到。
            // 保留 PROCESSING，等待可能到达的 Confirm 或由超时 Recovery 收敛。
            LOGGER.error(
                    "RabbitMQ publish outcome is unknown; outbox remains PROCESSING, outboxId={}",
                    correlationData.getOutboxId(),
                    publishFailure
            );
        }
    }

    private OutboxCorrelationData buildCorrelationData(OutboxEvent event) {
        if (event == null) {
            throw new IllegalStateException("Claimed outbox event must not be null");
        }
        if (event.getId() == null || event.getId() <= 0) {
            throw new IllegalStateException("Claimed outbox event has invalid id");
        }
        if (event.getProcessingToken() == null || event.getProcessingToken().isBlank()) {
            throw new IllegalStateException(
                    "Claimed outbox event has invalid processingToken, id=" + event.getId()
            );
        }
        if (event.getRetryCount() == null || event.getRetryCount() < 0) {
            throw new IllegalStateException(
                    "Claimed outbox event has invalid retryCount, id=" + event.getId()
            );
        }
        return new OutboxCorrelationData(
                event.getId(),
                event.getProcessingToken(),
                event.getRetryCount()
        );
    }

    private Message buildMessage(OutboxEvent event) {
        if (!OutboxEventTypeConstants.LOTTERY_SUCCESS.equals(event.getEventType())) {
            throw new IllegalArgumentException(
                    "Unsupported outbox eventType: " + event.getEventType()
            );
        }
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            throw new IllegalArgumentException("Outbox payload must not be blank");
        }

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setHeader(LotteryEventRabbitTopology.EVENT_ID_HEADER, event.getId());
        properties.setHeader(LotteryEventRabbitTopology.EVENT_TYPE_HEADER, event.getEventType());
        return new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);
    }

    private void handleConfirm(
            OutboxCorrelationData correlationData,
            CorrelationData.Confirm confirm,
            Throwable completionFailure
    ) {
        if (completionFailure != null || confirm == null) {
            // 异常完成或缺失 Confirm 都无法判断 Broker 是否已接收，不能主动回退。
            LOGGER.error(
                    "RabbitMQ confirm outcome is unknown; outbox remains PROCESSING, outboxId={}",
                    correlationData.getOutboxId(),
                    completionFailure
            );
            return;
        }

        if (!confirm.ack()) {
            if (confirm.reason() != null) {
                // Spring 在连接或 Channel 关闭时会为未决 Confirm 生成带原因的合成 NACK；
                // 此时 Broker 可能已经收件，必须保留 PROCESSING 交给超时 Recovery 判定重发。
                LOGGER.warn(
                        "RabbitMQ confirm was synthesized after channel shutdown; "
                                + "outbox remains PROCESSING, outboxId={}, reason={}",
                        correlationData.getOutboxId(),
                        confirm.reason()
                );
                return;
            }
            markPublishFailed(correlationData, confirm.reason());
            return;
        }

        ReturnedMessage returnedMessage = correlationData.getReturned();
        if (returnedMessage != null) {
            markPublishFailed(correlationData, describeReturnedMessage(returnedMessage));
            return;
        }

        try {
            boolean transitioned = outboxStateService.markPublished(
                    correlationData.getOutboxId(),
                    correlationData.getProcessingToken(),
                    correlationData.getRetryCount()
            );
            if (!transitioned) {
                LOGGER.debug(
                        "Ignore stale RabbitMQ ACK, outboxId={}",
                        correlationData.getOutboxId()
                );
            }
        } catch (RuntimeException stateUpdateFailure) {
            // Broker 已明确 ACK，数据库写失败不能伪装为 publish 失败；保留 PROCESSING 给 Recovery。
            LOGGER.error(
                    "Broker ACK received but Outbox state update failed; "
                            + "outbox remains PROCESSING, outboxId={}",
                    correlationData.getOutboxId(),
                    stateUpdateFailure
            );
        }
    }

    private void markPublishFailed(OutboxCorrelationData correlationData, String failureReason) {
        try {
            boolean transitioned = outboxStateService.markPublishFailed(
                    correlationData.getOutboxId(),
                    correlationData.getProcessingToken(),
                    correlationData.getRetryCount(),
                    failureReason
            );
            if (!transitioned) {
                LOGGER.debug(
                        "Ignore stale RabbitMQ failure result, outboxId={}",
                        correlationData.getOutboxId()
                );
            }
        } catch (RuntimeException stateUpdateFailure) {
            // 状态写入失败本身不是新的 Broker 失败，不能再次执行失败回退。
            LOGGER.error(
                    "Failed to persist RabbitMQ publish failure; "
                            + "outbox remains PROCESSING, outboxId={}",
                    correlationData.getOutboxId(),
                    stateUpdateFailure
            );
        }
    }

    private String describeReturnedMessage(ReturnedMessage returnedMessage) {
        return "message returned by broker: replyCode=" + returnedMessage.getReplyCode()
                + ", replyText=" + returnedMessage.getReplyText()
                + ", exchange=" + returnedMessage.getExchange()
                + ", routingKey=" + returnedMessage.getRoutingKey();
    }

    private String describeLocalPreparationFailure(RuntimeException failure) {
        String message = failure.getMessage();
        return "local publish preparation failed: " + failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}

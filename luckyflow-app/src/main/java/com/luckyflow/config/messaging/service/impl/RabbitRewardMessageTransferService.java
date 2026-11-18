package com.luckyflow.config.messaging.service.impl;

import com.luckyflow.config.messaging.LotteryEventRabbitTopology;
import com.luckyflow.config.messaging.LotteryRewardConsumerProperties;
import com.luckyflow.config.messaging.RewardMessageTransferException;
import com.luckyflow.config.messaging.service.RewardMessageTransferService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 使用 Publisher Confirm 与 Return 证明目标队列已经可靠接收失败消息。 */
@Service
@ConditionalOnProperty(
        prefix = "luckyflow.rabbit.reward-consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RabbitRewardMessageTransferService implements RewardMessageTransferService {

    private final RabbitTemplate rabbitTemplate;
    private final LotteryRewardConsumerProperties properties;

    public RabbitRewardMessageTransferService(
            RabbitTemplate rabbitTemplate,
            LotteryRewardConsumerProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void transferToRetry(
            Message originalMessage,
            int nextRetryCount,
            Throwable failure
    ) {
        String routingKey = switch (nextRetryCount) {
            case 1 -> LotteryEventRabbitTopology.REWARD_RETRY_1_ROUTING_KEY;
            case 2 -> LotteryEventRabbitTopology.REWARD_RETRY_2_ROUTING_KEY;
            default -> throw new IllegalArgumentException(
                    "Unsupported reward retry stage: " + nextRetryCount
            );
        };
        publishReliably(
                LotteryEventRabbitTopology.REWARD_RETRY_EXCHANGE,
                routingKey,
                buildTransferMessage(originalMessage, nextRetryCount, failure)
        );
    }

    @Override
    public void transferToDeadLetter(
            Message originalMessage,
            int retryCount,
            Throwable failure
    ) {
        publishReliably(
                LotteryEventRabbitTopology.REWARD_DEAD_LETTER_EXCHANGE,
                LotteryEventRabbitTopology.REWARD_DEAD_LETTER_ROUTING_KEY,
                buildTransferMessage(originalMessage, retryCount, failure)
        );
    }

    private void publishReliably(String exchange, String routingKey, Message message) {
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.send(exchange, routingKey, message, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    properties.getConfirmTimeoutMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (confirm == null || !confirm.ack()) {
                throw new RewardMessageTransferException(
                        "RabbitMQ rejected reward failure transfer: "
                                + (confirm == null ? "missing confirm" : confirm.reason())
                );
            }
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                throw new RewardMessageTransferException(
                        "RabbitMQ returned reward failure transfer: replyCode="
                                + returned.getReplyCode()
                                + ", replyText=" + returned.getReplyText()
                );
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RewardMessageTransferException(
                    "Interrupted while waiting for reward failure transfer confirm",
                    interrupted
            );
        } catch (TimeoutException timeout) {
            // Confirm 超时属于 Broker 接收结果未知；抛出后让原消息留在正式 Quorum Queue 中重投。
            throw new RewardMessageTransferException(
                    "Timed out waiting for reward failure transfer confirm",
                    timeout
            );
        } catch (ExecutionException confirmFailure) {
            throw new RewardMessageTransferException(
                    "Failed while waiting for reward failure transfer confirm",
                    confirmFailure.getCause() == null ? confirmFailure : confirmFailure.getCause()
            );
        } catch (RewardMessageTransferException transferFailure) {
            throw transferFailure;
        } catch (RuntimeException publishFailure) {
            throw new RewardMessageTransferException(
                    "Failed to publish reward failure transfer",
                    publishFailure
            );
        }
    }

    private Message buildTransferMessage(
            Message originalMessage,
            int retryCount,
            Throwable failure
    ) {
        if (originalMessage == null) {
            throw new IllegalArgumentException("Original reward message must not be null");
        }
        MessageProperties source = originalMessage.getMessageProperties();
        MessageProperties target = new MessageProperties();
        target.setContentType(
                source.getContentType() == null
                        ? MessageProperties.CONTENT_TYPE_JSON
                        : source.getContentType()
        );
        target.setContentEncoding(
                source.getContentEncoding() == null
                        ? StandardCharsets.UTF_8.name()
                        : source.getContentEncoding()
        );
        target.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        copyHeader(source, target, LotteryEventRabbitTopology.EVENT_ID_HEADER);
        copyHeader(source, target, LotteryEventRabbitTopology.EVENT_TYPE_HEADER);
        target.setHeader(LotteryEventRabbitTopology.RETRY_COUNT_HEADER, retryCount);
        if (failure != null) {
            target.setHeader(
                    LotteryEventRabbitTopology.FAILURE_TYPE_HEADER,
                    failure.getClass().getName()
            );
            target.setHeader(
                    LotteryEventRabbitTopology.FAILURE_MESSAGE_HEADER,
                    safeFailureMessage(failure)
            );
        }
        return new Message(originalMessage.getBody().clone(), target);
    }

    private void copyHeader(
            MessageProperties source,
            MessageProperties target,
            String headerName
    ) {
        Object value = source.getHeader(headerName);
        if (value != null) {
            target.setHeader(headerName, value);
        }
    }

    private String safeFailureMessage(Throwable failure) {
        // 异常原文可能包含 SQL、连接信息或业务敏感数据，消息头只保留稳定且无敏感内容的摘要。
        return "Reward processing failed: " + failure.getClass().getSimpleName();
    }
}

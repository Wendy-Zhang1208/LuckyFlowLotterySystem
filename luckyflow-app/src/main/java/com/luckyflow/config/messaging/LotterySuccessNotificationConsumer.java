package com.luckyflow.config.messaging;

import com.rabbitmq.client.Channel;
import com.luckyflow.lottery.constant.OutboxEventTypeConstants;
import com.luckyflow.lottery.model.LotterySuccessEvent;
import com.luckyflow.notification.service.LotterySuccessNotificationConsumptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 正式中奖通知 Consumer，仅负责 AMQP 协议校验和业务 Service 调用，不直接访问通知 Mapper。
 */
@Component
@ConditionalOnProperty(
        prefix = "luckyflow.rabbit.notification-consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LotterySuccessNotificationConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LotterySuccessNotificationConsumer.class);

    private final LotterySuccessNotificationConsumptionService consumptionService;
    private final ObjectMapper objectMapper;

    public LotterySuccessNotificationConsumer(
            LotterySuccessNotificationConsumptionService consumptionService,
            ObjectMapper objectMapper
    ) {
        this.consumptionService = consumptionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 事务提交后才 ACK；失败用 basic.reject 计入 Quorum 投递上限，避免 nack 无限重投。
     */
    @RabbitListener(
            id = "lotterySuccessNotificationConsumer",
            queues = LotteryEventRabbitTopology.NOTIFICATION_QUEUE,
            containerFactory = "lotteryNotificationRabbitListenerContainerFactory"
    )
    public void receiveAndAcknowledge(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            receive(message);
        } catch (RuntimeException failure) {
            // RabbitMQ 4.3 的 nack 不增加失败计数；reject 才能触发既有有限重试和至少一次死信转移。
            // 仅记录错误类型，不把消息体、联系方式或数据库异常明细写入日志。
            LOGGER.warn("Lottery notification rejected for bounded broker retry, eventId={}, failureType={}",
                    message.getMessageProperties().getHeader(LotteryEventRabbitTopology.EVENT_ID_HEADER),
                    failure.getClass().getSimpleName());
            channel.basicReject(deliveryTag, true);
            return;
        }
        // ACK 失败不得补记成功或主动再 ACK；连接关闭后的重投由 consumed_event 唯一键收敛。
        channel.basicAck(deliveryTag, false);
    }

    public void receive(Message message) {
        Message requiredMessage = requireMessage(message);
        validateEventType(requiredMessage);
        Long eventId = readEventId(requiredMessage);
        LotterySuccessEvent event = readEvent(requiredMessage);

        consumptionService.consume(eventId, event);
        LOGGER.info(
                "Lottery notification intent consumed, eventId={}, winningRecordId={}",
                eventId,
                event.winningRecordId()
        );
    }

    private Long readEventId(Message message) {
        Object value = message.getMessageProperties()
                .getHeader(LotteryEventRabbitTopology.EVENT_ID_HEADER);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Lottery notification eventId header must be positive"
            );
        }
        long eventId = requireExactLong(number, "eventId");
        if (eventId <= 0) {
            throw new IllegalArgumentException(
                    "Lottery notification eventId header must be positive"
            );
        }
        return eventId;
    }

    private void validateEventType(Message message) {
        Object value = message.getMessageProperties()
                .getHeader(LotteryEventRabbitTopology.EVENT_TYPE_HEADER);
        if (!OutboxEventTypeConstants.LOTTERY_SUCCESS.equals(value)) {
            throw new IllegalArgumentException(
                    "Unsupported lottery notification eventType: " + value
            );
        }
    }

    private LotterySuccessEvent readEvent(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), LotterySuccessEvent.class);
        } catch (JacksonException invalidJson) {
            throw new IllegalArgumentException(
                    "Invalid lottery success notification event JSON",
                    invalidJson
            );
        }
    }

    private Message requireMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException(
                    "Lottery notification message must not be null"
            );
        }
        return message;
    }

    private long requireExactLong(Number value, String headerName) {
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return value.longValue();
        }
        throw new IllegalArgumentException(
                "Notification " + headerName + " header must be an exact integer"
        );
    }
}

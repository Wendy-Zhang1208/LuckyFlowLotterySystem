package com.luckyflow.config.messaging;

import com.luckyflow.config.messaging.service.LotterySuccessEventConsumptionService;
import com.luckyflow.config.messaging.service.RewardMessageTransferService;
import com.luckyflow.exception.BusinessException;
import com.luckyflow.lottery.constant.OutboxEventTypeConstants;
import com.luckyflow.lottery.model.LotterySuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 正式中奖奖励 Consumer：负责消息协议适配、异常分类以及可靠转移，不直接访问业务 Mapper。
 */
@Component
@ConditionalOnProperty(
        prefix = "luckyflow.rabbit.reward-consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LotterySuccessRewardConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LotterySuccessRewardConsumer.class);

    private final LotterySuccessEventConsumptionService consumptionService;
    private final RewardMessageTransferService transferService;
    private final ObjectMapper objectMapper;

    public LotterySuccessRewardConsumer(
            LotterySuccessEventConsumptionService consumptionService,
            RewardMessageTransferService transferService,
            ObjectMapper objectMapper
    ) {
        this.consumptionService = consumptionService;
        this.transferService = transferService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(
            id = "lotterySuccessRewardConsumer",
            queues = LotteryEventRabbitTopology.REWARD_QUEUE,
            containerFactory = "lotteryRewardRabbitListenerContainerFactory"
    )
    public void receive(Message message) {
        int retryCount = 0;
        try {
            retryCount = readRetryCount(message);
            validateEventType(message);
            Long eventId = readEventId(message);
            LotterySuccessEvent event = readEvent(message);

            // REQUIRED 业务事务已经提交后方法才返回，AUTO ACK 因而不会先于 MySQL COMMIT。
            consumptionService.consume(eventId, event);
            LOGGER.info(
                    "Lottery reward consumed, eventId={}, winningRecordId={}, prizeType={}",
                    eventId,
                    event.winningRecordId(),
                    event.prizeType()
            );
        } catch (RuntimeException processingFailure) {
            handleFailure(message, retryCount, processingFailure);
        }
    }

    private void handleFailure(
            Message message,
            int retryCount,
            RuntimeException processingFailure
    ) {
        if (isDeterministic(processingFailure)) {
            transferService.transferToDeadLetter(message, retryCount, processingFailure);
            return;
        }
        if (retryCount == 0) {
            transferService.transferToRetry(message, 1, processingFailure);
            return;
        }
        if (retryCount == 1) {
            transferService.transferToRetry(message, 2, processingFailure);
            return;
        }
        transferService.transferToDeadLetter(message, retryCount, processingFailure);
    }

    private int readRetryCount(Message message) {
        Object value = requireMessage(message).getMessageProperties()
                .getHeader(LotteryEventRabbitTopology.RETRY_COUNT_HEADER);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Reward retryCount header must be numeric");
        }
        int retryCount = requireExactInt(number, "retryCount");
        if (retryCount < 0 || retryCount > 2) {
            throw new IllegalArgumentException("Reward retryCount header must be between 0 and 2");
        }
        return retryCount;
    }

    private Long readEventId(Message message) {
        Object value = requireMessage(message).getMessageProperties()
                .getHeader(LotteryEventRabbitTopology.EVENT_ID_HEADER);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Lottery reward eventId header must be positive");
        }
        long eventId = requireExactLong(number, "eventId");
        if (eventId <= 0) {
            throw new IllegalArgumentException("Lottery reward eventId header must be positive");
        }
        return eventId;
    }

    private void validateEventType(Message message) {
        Object value = requireMessage(message).getMessageProperties()
                .getHeader(LotteryEventRabbitTopology.EVENT_TYPE_HEADER);
        if (!OutboxEventTypeConstants.LOTTERY_SUCCESS.equals(value)) {
            throw new IllegalArgumentException("Unsupported lottery reward eventType: " + value);
        }
    }

    private LotterySuccessEvent readEvent(Message message) {
        try {
            return objectMapper.readValue(
                    requireMessage(message).getBody(),
                    LotterySuccessEvent.class
            );
        } catch (JacksonException invalidJson) {
            throw new IllegalArgumentException("Invalid lottery success event JSON", invalidJson);
        }
    }

    private Message requireMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Lottery reward message must not be null");
        }
        return message;
    }

    private int requireExactInt(Number value, String headerName) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return value.intValue();
        }
        if (value instanceof Long longValue
                && longValue >= Integer.MIN_VALUE
                && longValue <= Integer.MAX_VALUE) {
            return longValue.intValue();
        }
        throw new IllegalArgumentException(
                "Reward " + headerName + " header must be an exact integer"
        );
    }

    private long requireExactLong(Number value, String headerName) {
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return value.longValue();
        }
        throw new IllegalArgumentException(
                "Reward " + headerName + " header must be an exact integer"
        );
    }

    private boolean isDeterministic(Throwable failure) {
        boolean deterministicFailureFound = false;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataIntegrityViolationException) {
                deterministicFailureFound = true;
                continue;
            }
            if (current instanceof DataAccessException) {
                // 数据库连接、锁等待等临时故障优先于外层业务包装异常，不能被误送最终 DLQ。
                return false;
            }
            if (current instanceof IllegalArgumentException
                    || current instanceof IllegalStateException
                    || current instanceof BusinessException) {
                deterministicFailureFound = true;
            }
        }
        // 未识别异常按临时故障处理，经过有限业务重试后再进入 DLQ，避免一次偶发故障直接丢失机会。
        return deterministicFailureFound;
    }
}

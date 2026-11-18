package com.luckyflow.config.messaging.service.impl;

import com.luckyflow.config.messaging.constant.LotterySuccessConsumerConstants;
import com.luckyflow.config.messaging.service.ConsumedEventService;
import com.luckyflow.config.messaging.service.LotterySuccessEventConsumptionService;
import com.luckyflow.fulfillment.service.FulfillmentService;
import com.luckyflow.fulfillment.service.VirtualPrizeService;
import com.luckyflow.lottery.model.LotterySuccessEvent;
import com.luckyflow.lottery.model.WinningFact;
import com.luckyflow.lottery.service.Interface.WinningFactService;
import com.luckyflow.marketing.constant.PrizeTypeConstants;
import com.luckyflow.user.service.PointRewardService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import static com.luckyflow.lottery.constant.DrawResultStatusConstants.DRAW_RESULT_WIN;

/**
 * 中奖事实消费的应用层编排，不包含 RabbitMQ 监听职责。
 */
@Service
public class LotterySuccessEventConsumptionServiceImpl
        implements LotterySuccessEventConsumptionService {

    private final ConsumedEventService consumedEventService;
    private final FulfillmentService fulfillmentService;
    private final VirtualPrizeService virtualPrizeService;
    private final PointRewardService pointRewardService;
    private final WinningFactService winningFactService;
    private final Clock clock;

    public LotterySuccessEventConsumptionServiceImpl(
            ConsumedEventService consumedEventService,
            FulfillmentService fulfillmentService,
            VirtualPrizeService virtualPrizeService,
            PointRewardService pointRewardService,
            WinningFactService winningFactService,
            Clock clock
    ) {
        this.consumedEventService = consumedEventService;
        this.fulfillmentService = fulfillmentService;
        this.virtualPrizeService = virtualPrizeService;
        this.pointRewardService = pointRewardService;
        this.winningFactService = winningFactService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void consume(Long eventId, LotterySuccessEvent event) {
        Objects.requireNonNull(event, "Lottery success event must not be null");

        LocalDateTime consumeTime = LocalDateTime.now(clock);
        // 消息幂等记录与具体奖励共用当前 MySQL 事务，任何奖励失败都必须整体回滚。
        boolean firstConsumption = consumedEventService.tryConsume(
                LotterySuccessConsumerConstants.REWARD_CONSUMER_NAME,
                eventId,
                consumeTime
        );
        if (!firstConsumption) {
            return;
        }

        validateEvent(event);
        WinningFact winningFact = validateWinningFact(
                event,
                winningFactService.findWinningFact(event.winningRecordId())
        );

        switch (event.prizeType()) {
            case PrizeTypeConstants.PHYSICAL ->
                    fulfillmentService.initializePhysicalPrizeOrder(
                            event.winningRecordId(),
                            event.userId(),
                            consumeTime
                    );
            case PrizeTypeConstants.VIRTUAL ->
                    virtualPrizeService.initializeVirtualPrize(
                            event.winningRecordId(),
                            event.userId(),
                            event.prizeId(),
                            consumeTime
                    );
            case PrizeTypeConstants.POINTS ->
                    pointRewardService.grantLotteryReward(
                            event.winningRecordId(),
                            event.userId(),
                            requirePositiveIntegralPoints(winningFact.prizeValueSnapshot()),
                            consumeTime
                    );
            default -> throw new IllegalStateException(
                    "Unsupported lottery reward prize type: " + event.prizeType()
            );
        }
    }

    private void validateEvent(LotterySuccessEvent event) {
        requirePositive(event.winningRecordId(), "winningRecordId");
        requirePositive(event.userId(), "userId");
        requirePositive(event.activityId(), "activityId");
        requirePositive(event.activityPrizeId(), "activityPrizeId");
        requirePositive(event.prizeId(), "prizeId");
        if (event.prizeType() == null) {
            throw new IllegalArgumentException("Lottery success event prizeType must not be null");
        }
    }

    private WinningFact validateWinningFact(
            LotterySuccessEvent event,
            WinningFact winningFact
    ) {
        if (winningFact == null) {
            throw new IllegalStateException("Winning fact does not exist");
        }
        if (!Integer.valueOf(DRAW_RESULT_WIN).equals(winningFact.resultStatus())
                || !Objects.equals(event.winningRecordId(), winningFact.winningRecordId())
                || !Objects.equals(event.userId(), winningFact.userId())
                || !Objects.equals(event.activityId(), winningFact.activityId())
                || !Objects.equals(event.activityPrizeId(), winningFact.activityPrizeId())
                || !Objects.equals(event.prizeId(), winningFact.prizeId())
                || !Objects.equals(event.prizeType(), winningFact.prizeTypeSnapshot())) {
            throw new IllegalStateException(
                    "Lottery success event conflicts with the stored winning fact"
            );
        }
        return winningFact;
    }

    private long requirePositiveIntegralPoints(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException("Point reward value must be positive");
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException invalidPointValue) {
            throw new IllegalStateException(
                    "Point reward value must be an integer",
                    invalidPointValue
            );
        }
    }

    private void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    "Lottery success event " + fieldName + " must be positive"
            );
        }
    }
}

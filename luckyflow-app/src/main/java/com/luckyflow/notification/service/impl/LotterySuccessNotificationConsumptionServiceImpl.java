package com.luckyflow.notification.service.impl;

import com.luckyflow.config.messaging.constant.LotterySuccessConsumerConstants;
import com.luckyflow.config.messaging.service.ConsumedEventService;
import com.luckyflow.lottery.constant.DrawResultStatusConstants;
import com.luckyflow.lottery.model.LotterySuccessEvent;
import com.luckyflow.lottery.model.WinningFact;
import com.luckyflow.lottery.service.Interface.WinningFactService;
import com.luckyflow.notification.constant.NotificationChannelConstants;
import com.luckyflow.notification.service.LotterySuccessNotificationConsumptionService;
import com.luckyflow.notification.service.NotificationIntentService;
import com.luckyflow.user.model.UserContact;
import com.luckyflow.user.service.UserContactService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 中奖通知消费的事务编排层，不承担 RabbitMQ 协议适配或外部服务商调用。
 */
@Service
public class LotterySuccessNotificationConsumptionServiceImpl
        implements LotterySuccessNotificationConsumptionService {

    private final ConsumedEventService consumedEventService;
    private final WinningFactService winningFactService;
    private final UserContactService userContactService;
    private final NotificationIntentService notificationIntentService;
    private final Clock clock;

    public LotterySuccessNotificationConsumptionServiceImpl(
            ConsumedEventService consumedEventService,
            WinningFactService winningFactService,
            UserContactService userContactService,
            NotificationIntentService notificationIntentService,
            Clock clock
    ) {
        this.consumedEventService = consumedEventService;
        this.winningFactService = winningFactService;
        this.userContactService = userContactService;
        this.notificationIntentService = notificationIntentService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void consume(Long eventId, LotterySuccessEvent event) {
        Objects.requireNonNull(event, "Lottery success event must not be null");
        requirePositive(eventId, "eventId");

        LocalDateTime now = LocalDateTime.now(clock);
        // 消费标记与两个渠道的通知意图共用当前事务，任一意图失败都不能留下“已消费”假象。
        boolean firstConsumption = consumedEventService.tryConsume(
                LotterySuccessConsumerConstants.NOTIFICATION_CONSUMER_NAME,
                eventId,
                now
        );
        if (!firstConsumption) {
            return;
        }

        validateEvent(event);
        validateWinningFact(event, winningFactService.findWinningFact(event.winningRecordId()));
        UserContact contact = userContactService.findContact(event.userId());
        if (contact == null || !Objects.equals(contact.userId(), event.userId())) {
            throw new IllegalStateException("Notification user contact does not exist");
        }

        String phone = normalize(contact.phone());
        String email = normalize(contact.email());
        if (phone == null && email == null) {
            throw new IllegalStateException("Notification user has no phone or email");
        }

        if (phone != null) {
            notificationIntentService.initialize(
                    event.winningRecordId(),
                    event.userId(),
                    NotificationChannelConstants.SMS,
                    phone,
                    now
            );
        }
        if (email != null) {
            notificationIntentService.initialize(
                    event.winningRecordId(),
                    event.userId(),
                    NotificationChannelConstants.EMAIL,
                    email,
                    now
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

    private void validateWinningFact(LotterySuccessEvent event, WinningFact winningFact) {
        if (winningFact == null) {
            throw new IllegalStateException("Winning fact does not exist");
        }
        if (!Integer.valueOf(DrawResultStatusConstants.DRAW_RESULT_WIN)
                .equals(winningFact.resultStatus())
                || !Objects.equals(event.winningRecordId(), winningFact.winningRecordId())
                || !Objects.equals(event.userId(), winningFact.userId())
                || !Objects.equals(event.activityId(), winningFact.activityId())
                || !Objects.equals(event.activityPrizeId(), winningFact.activityPrizeId())
                || !Objects.equals(event.prizeId(), winningFact.prizeId())) {
            throw new IllegalStateException(
                    "Lottery success event conflicts with the stored winning fact"
            );
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    "Lottery success event " + fieldName + " must be positive"
            );
        }
    }
}

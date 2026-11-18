package com.luckyflow.notification.service.impl;

import com.luckyflow.notification.config.NotificationProperties;
import com.luckyflow.notification.constant.NotificationChannelConstants;
import com.luckyflow.notification.constant.NotificationStatusConstants;
import com.luckyflow.notification.entity.NotificationRecord;
import com.luckyflow.notification.sender.EmailSender;
import com.luckyflow.notification.sender.NotificationSendCommand;
import com.luckyflow.notification.sender.SmsSender;
import com.luckyflow.notification.service.NotificationSendService;
import com.luckyflow.notification.service.NotificationStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** 按通知渠道执行一次事务外发送，并将明确结果交由状态服务原子落库。 */
@Service
public class NotificationSendServiceImpl implements NotificationSendService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSendServiceImpl.class);
    private static final int MAX_RECORDED_ERROR_LENGTH = 500;

    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final NotificationStateService notificationStateService;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationSendServiceImpl(
            EmailSender emailSender,
            SmsSender smsSender,
            NotificationStateService notificationStateService,
            NotificationProperties properties,
            Clock clock
    ) {
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.notificationStateService = notificationStateService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 外部发送不持有领取事务；旧 attempt 明确晚到成功仍可进入 SUCCESS，晚到失败会被 token 条件拒绝。
     */
    @Override
    public void send(NotificationRecord notificationRecord) {
        validateClaimedRecord(notificationRecord);
        boolean currentAttempt = notificationStateService.markSendingStarted(
                notificationRecord.getId(),
                notificationRecord.getProcessingToken(),
                notificationRecord.getRetryCount(),
                LocalDateTime.now(clock)
        );
        if (!currentAttempt) {
            // 已被超时恢复或重新领取的旧排队任务从未调用过服务商，必须在网络发送前终止。
            LOGGER.info(
                    "Skip stale queued notification attempt, id={}",
                    notificationRecord.getId()
            );
            return;
        }

        NotificationSendCommand command = new NotificationSendCommand(
                notificationRecord.getId(),
                notificationRecord.getWinningRecordId(),
                notificationRecord.getUserId(),
                notificationRecord.getRecipient()
        );

        try {
            dispatchToProvider(notificationRecord.getChannel(), command);
        } catch (RuntimeException providerFailure) {
            boolean failed = notificationStateService.markFailed(
                    notificationRecord.getId(),
                    notificationRecord.getProcessingToken(),
                    notificationRecord.getRetryCount(),
                    describeFailure(providerFailure),
                    LocalDateTime.now(clock)
            );
            if (!failed) {
                LOGGER.debug(
                        "Ignore stale notification failure, id={}",
                        notificationRecord.getId()
                );
            }
            return;
        }

        try {
            boolean succeeded = notificationStateService.markSucceeded(
                    notificationRecord.getId(),
                    LocalDateTime.now(clock)
            );
            if (!succeeded) {
                LOGGER.warn(
                        "Ignore provider success for missing or already terminal notification, id={}",
                        notificationRecord.getId()
                );
            }
        } catch (RuntimeException stateFailure) {
            // 服务商已经明确接受时不能再回退为失败，否则会主动制造重复发送；保留原状态交给后续核查。
            LOGGER.error(
                    "Notification provider accepted request but SUCCESS persistence failed, id={}",
                    notificationRecord.getId(),
                    stateFailure
            );
        }
    }

    private void dispatchToProvider(Integer channel, NotificationSendCommand command) {
        if (Integer.valueOf(NotificationChannelConstants.EMAIL).equals(channel)) {
            emailSender.send(command);
            return;
        }
        if (Integer.valueOf(NotificationChannelConstants.SMS).equals(channel)) {
            smsSender.send(command, properties.getProviderTimeout());
            return;
        }
        throw new IllegalArgumentException("Unsupported notification channel: " + channel);
    }

    private void validateClaimedRecord(NotificationRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Notification record must not be null");
        }
        if (!Integer.valueOf(NotificationStatusConstants.PROCESSING).equals(record.getStatus())) {
            throw new IllegalStateException("Notification must be PROCESSING before sending");
        }
        if (record.getProcessingToken() == null || record.getProcessingToken().isBlank()) {
            throw new IllegalStateException("Notification processingToken must not be blank");
        }
        if (record.getRetryCount() == null || record.getRetryCount() < 0) {
            throw new IllegalStateException("Notification retryCount must not be negative");
        }
    }

    private String describeFailure(RuntimeException failure) {
        // 外部异常文本可能包含手机号、邮箱或连接信息，只持久化稳定异常类型以避免泄露敏感数据。
        String description = "Notification provider failure: "
                + failure.getClass().getSimpleName();
        return description.length() <= MAX_RECORDED_ERROR_LENGTH
                ? description
                : description.substring(0, MAX_RECORDED_ERROR_LENGTH);
    }
}

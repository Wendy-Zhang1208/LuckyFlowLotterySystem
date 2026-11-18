package com.luckyflow.notification.service.impl;

import com.luckyflow.notification.constant.NotificationChannelConstants;
import com.luckyflow.notification.constant.NotificationStatusConstants;
import com.luckyflow.notification.entity.NotificationRecord;
import com.luckyflow.notification.mapper.NotificationRecordMapper;
import com.luckyflow.notification.service.NotificationIntentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** 基于中奖记录与渠道唯一键创建通知意图。 */
@Service
public class NotificationIntentServiceImpl implements NotificationIntentService {

    private static final int MAX_RECIPIENT_LENGTH = 320;

    private final NotificationRecordMapper notificationRecordMapper;

    public NotificationIntentServiceImpl(NotificationRecordMapper notificationRecordMapper) {
        this.notificationRecordMapper = notificationRecordMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void initialize(
            Long winningRecordId,
            Long userId,
            int channel,
            String recipient,
            LocalDateTime now
    ) {
        validateArguments(winningRecordId, userId, channel, recipient, now);

        NotificationRecord record = new NotificationRecord();
        record.setWinningRecordId(winningRecordId);
        record.setUserId(userId);
        record.setChannel(channel);
        record.setRecipient(recipient);
        record.setStatus(NotificationStatusConstants.PENDING);
        record.setRetryCount(0);
        record.setNextRetryTime(now);
        record.setProcessingToken(null);
        record.setProcessingTime(null);
        record.setSendTime(null);
        record.setLastError(null);
        record.setCreateTime(now);
        record.setUpdateTime(now);

        try {
            // 直接 INSERT 由数据库唯一键裁决并发；只有重复键才能进入业务幂等核验。
            int affectedRows = notificationRecordMapper.insert(record);
            if (affectedRows != 1) {
                throw new IllegalStateException("Failed to create notification intent");
            }
            if (record.getId() == null) {
                throw new IllegalStateException("Notification intent ID was not generated");
            }
        } catch (DuplicateKeyException duplicateIntent) {
            NotificationRecord existing =
                    notificationRecordMapper.selectByWinningRecordIdAndChannelForShare(
                            winningRecordId,
                            channel
                    );
            validateExisting(existing, winningRecordId, userId, channel);
        }
    }

    private void validateExisting(
            NotificationRecord existing,
            Long winningRecordId,
            Long userId,
            int channel
    ) {
        if (existing == null) {
            throw new IllegalStateException(
                    "Duplicate notification intent was reported but the existing record was not found"
            );
        }
        // recipient 是首次创建通知意图时的历史快照，用户后续修改联系方式不应覆盖快照或破坏业务幂等。
        if (!Objects.equals(existing.getWinningRecordId(), winningRecordId)
                || !Objects.equals(existing.getUserId(), userId)
                || !Objects.equals(existing.getChannel(), channel)) {
            throw new IllegalStateException(
                    "Existing notification intent conflicts with the current winning fact"
            );
        }
    }

    private void validateArguments(
            Long winningRecordId,
            Long userId,
            int channel,
            String recipient,
            LocalDateTime now
    ) {
        if (winningRecordId == null || winningRecordId <= 0) {
            throw new IllegalArgumentException("Winning record ID must be positive");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        if (!NotificationChannelConstants.isSupported(channel)) {
            throw new IllegalArgumentException("Unsupported notification channel: " + channel);
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Notification recipient must not be blank");
        }
        if (recipient.length() > MAX_RECIPIENT_LENGTH) {
            throw new IllegalArgumentException("Notification recipient is too long");
        }
        Objects.requireNonNull(now, "Notification creation time must not be null");
    }
}

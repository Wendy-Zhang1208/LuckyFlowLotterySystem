package com.luckyflow.notification.service.impl;

import com.luckyflow.notification.constant.NotificationStatusConstants;
import com.luckyflow.notification.mapper.NotificationRecordMapper;
import com.luckyflow.notification.service.NotificationStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 通过状态、token 和重试次数条件保证迟到失败不能覆盖新结果。 */
@Service
public class NotificationStateServiceImpl implements NotificationStateService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int MAX_LAST_ERROR_LENGTH = 500;

    private final NotificationRecordMapper notificationRecordMapper;

    public NotificationStateServiceImpl(NotificationRecordMapper notificationRecordMapper) {
        this.notificationRecordMapper = notificationRecordMapper;
    }

    /**
     * 只有数据库中仍处于当前 PROCESSING attempt 的任务才允许开始网络发送，同时刷新恢复超时起点。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean markSendingStarted(
            Long notificationId,
            String processingToken,
            int expectedRetryCount,
            LocalDateTime processingTime
    ) {
        requirePositive(notificationId, "notificationId");
        if (processingToken == null || processingToken.isBlank()) {
            throw new IllegalArgumentException("Notification processingToken must not be blank");
        }
        if (expectedRetryCount < 0 || expectedRetryCount >= MAX_RETRY_COUNT) {
            throw new IllegalArgumentException("Notification retryCount must be between 0 and 2");
        }
        if (processingTime == null) {
            throw new IllegalArgumentException("Notification processingTime must not be null");
        }
        return notificationRecordMapper.markSendingStarted(
                notificationId,
                processingToken,
                expectedRetryCount,
                processingTime
        ) == 1;
    }

    /**
     * 成功不校验 processingToken，以接纳确实调用过服务商的迟到成功；SQL 保证 SUCCESS 不会被再次改写。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean markSucceeded(Long notificationId, LocalDateTime sendTime) {
        requirePositive(notificationId, "notificationId");
        if (sendTime == null) {
            throw new IllegalArgumentException("Notification sendTime must not be null");
        }
        return notificationRecordMapper.markSucceeded(
                notificationId,
                sendTime,
                sendTime
        ) == 1;
    }

    /**
     * 当前 attempt 失败后增加一次计数：第一次等待 1 分钟、第二次等待 5 分钟、第三次进入人工处理。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean markFailed(
            Long notificationId,
            String processingToken,
            int expectedRetryCount,
            String lastError,
            LocalDateTime failureTime
    ) {
        requirePositive(notificationId, "notificationId");
        if (processingToken == null || processingToken.isBlank()) {
            throw new IllegalArgumentException("Notification processingToken must not be blank");
        }
        if (expectedRetryCount < 0 || expectedRetryCount >= MAX_RETRY_COUNT) {
            throw new IllegalArgumentException("Notification retryCount must be between 0 and 2");
        }
        if (failureTime == null) {
            throw new IllegalArgumentException("Notification failureTime must not be null");
        }

        int failedAttempts = expectedRetryCount + 1;
        int targetStatus;
        LocalDateTime nextRetryTime;
        if (failedAttempts < MAX_RETRY_COUNT) {
            targetStatus = NotificationStatusConstants.PENDING;
            nextRetryTime = failedAttempts == 1
                    ? failureTime.plusMinutes(1)
                    : failureTime.plusMinutes(5);
        } else {
            targetStatus = NotificationStatusConstants.MANUAL_REVIEW;
            nextRetryTime = null;
        }

        return notificationRecordMapper.markFailed(
                notificationId,
                processingToken,
                expectedRetryCount,
                targetStatus,
                nextRetryTime,
                normalizeLastError(lastError),
                failureTime
        ) == 1;
    }

    private String normalizeLastError(String lastError) {
        String normalized = lastError == null || lastError.isBlank()
                ? "Notification provider did not accept the request"
                : lastError.strip();
        return normalized.length() <= MAX_LAST_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_LAST_ERROR_LENGTH);
    }

    private void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}

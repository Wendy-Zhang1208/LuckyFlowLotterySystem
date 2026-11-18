package com.luckyflow.notification.service.impl;

import com.luckyflow.notification.constant.NotificationStatusConstants;
import com.luckyflow.notification.entity.NotificationRecord;
import com.luckyflow.notification.mapper.NotificationRecordMapper;
import com.luckyflow.notification.service.NotificationRecoveryService;
import com.luckyflow.notification.service.NotificationStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 扫描 JVM 宕机或供应商调用长期无结果后遗留的 PROCESSING 记录。 */
@Service
public class NotificationRecoveryServiceImpl implements NotificationRecoveryService {

    private static final String SEND_TIMEOUT_ERROR = "Notification send timed out";

    private final NotificationRecordMapper notificationRecordMapper;
    private final NotificationStateService notificationStateService;
    private final Clock clock;

    public NotificationRecoveryServiceImpl(
            NotificationRecordMapper notificationRecordMapper,
            NotificationStateService notificationStateService,
            Clock clock
    ) {
        this.notificationRecordMapper = notificationRecordMapper;
        this.notificationStateService = notificationStateService;
        this.clock = clock;
    }

    /**
     * 超时查询和条件失败迁移处在同一短事务；SQL token fencing 防止覆盖已成功或已重新领取的 attempt。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverTimedOut(Duration processingTimeout, int batchSize) {
        if (processingTimeout == null
                || processingTimeout.isZero()
                || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("Notification processingTimeout must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Notification batch size must be positive");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minus(processingTimeout);
        List<NotificationRecord> timedOutRecords =
                notificationRecordMapper.selectTimedOutProcessingForUpdate(cutoff, batchSize);
        if (timedOutRecords == null || timedOutRecords.isEmpty()) {
            return 0;
        }

        for (NotificationRecord record : timedOutRecords) {
            validateTimedOutRecord(record);
            boolean recovered = notificationStateService.markFailed(
                    record.getId(),
                    record.getProcessingToken(),
                    record.getRetryCount(),
                    SEND_TIMEOUT_ERROR,
                    now
            );
            if (!recovered) {
                throw new IllegalStateException(
                        "Failed to recover timed-out notification, id=" + record.getId()
                );
            }
        }
        return timedOutRecords.size();
    }

    private void validateTimedOutRecord(NotificationRecord record) {
        if (record == null || record.getId() == null || record.getId() <= 0) {
            throw new IllegalStateException("Timed-out notification has invalid id");
        }
        if (!Integer.valueOf(NotificationStatusConstants.PROCESSING).equals(record.getStatus())) {
            throw new IllegalStateException(
                    "Timed-out notification is not PROCESSING, id=" + record.getId()
            );
        }
        if (record.getProcessingToken() == null || record.getProcessingToken().isBlank()) {
            throw new IllegalStateException(
                    "Timed-out notification has invalid processingToken, id=" + record.getId()
            );
        }
        if (record.getRetryCount() == null || record.getRetryCount() < 0) {
            throw new IllegalStateException(
                    "Timed-out notification has invalid retryCount, id=" + record.getId()
            );
        }
    }
}

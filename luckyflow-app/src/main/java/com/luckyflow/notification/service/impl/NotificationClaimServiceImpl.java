package com.luckyflow.notification.service.impl;

import com.luckyflow.notification.constant.NotificationStatusConstants;
import com.luckyflow.notification.entity.NotificationRecord;
import com.luckyflow.notification.mapper.NotificationRecordMapper;
import com.luckyflow.notification.service.NotificationClaimService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 使用行锁并发安全地领取到期通知，方法内不执行任何外部网络调用。 */
@Service
public class NotificationClaimServiceImpl implements NotificationClaimService {

    private final NotificationRecordMapper notificationRecordMapper;
    private final Clock clock;

    public NotificationClaimServiceImpl(
            NotificationRecordMapper notificationRecordMapper,
            Clock clock
    ) {
        this.notificationRecordMapper = notificationRecordMapper;
        this.clock = clock;
    }

    /**
     * 查询锁定和 PENDING -> PROCESSING 必须在同一短事务完成；每条记录使用独立 token 隔离迟到回调。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<NotificationRecord> claimBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Notification batch size must be positive");
        }

        LocalDateTime processingTime = LocalDateTime.now(clock);
        List<NotificationRecord> records = notificationRecordMapper.selectPendingForUpdate(
                processingTime,
                batchSize
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        for (NotificationRecord record : records) {
            requireClaimableRecord(record);
            String processingToken = UUID.randomUUID().toString();
            int affectedRows = notificationRecordMapper.markProcessing(
                    record.getId(),
                    processingToken,
                    processingTime
            );
            if (affectedRows != 1) {
                throw new IllegalStateException(
                        "Failed to claim notification, id=" + record.getId()
                );
            }

            record.setStatus(NotificationStatusConstants.PROCESSING);
            record.setProcessingToken(processingToken);
            record.setProcessingTime(processingTime);
            record.setNextRetryTime(null);
            record.setUpdateTime(processingTime);
        }
        return records;
    }

    private void requireClaimableRecord(NotificationRecord record) {
        if (record == null || record.getId() == null || record.getId() <= 0) {
            throw new IllegalStateException("Claimed notification has invalid id");
        }
        if (!Integer.valueOf(NotificationStatusConstants.PENDING).equals(record.getStatus())) {
            throw new IllegalStateException(
                    "Claimed notification is not PENDING, id=" + record.getId()
            );
        }
        if (record.getRetryCount() == null || record.getRetryCount() < 0) {
            throw new IllegalStateException(
                    "Claimed notification has invalid retryCount, id=" + record.getId()
            );
        }
    }
}

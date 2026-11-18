package com.luckyflow.notification.service;

import com.luckyflow.notification.entity.NotificationRecord;

import java.util.List;

/** 在短 MySQL 事务内领取当前可发送的通知。 */
public interface NotificationClaimService {

    /** 查询并锁定到期 PENDING 记录，将其迁移为 PROCESSING 后返回。 */
    List<NotificationRecord> claimBatch(int batchSize);
}

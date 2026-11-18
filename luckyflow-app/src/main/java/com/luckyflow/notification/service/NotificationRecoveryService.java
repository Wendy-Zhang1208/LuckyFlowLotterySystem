package com.luckyflow.notification.service;

import java.time.Duration;

/** 恢复长时间停留在 PROCESSING 的通知 attempt。 */
public interface NotificationRecoveryService {

    /** 扫描并按当前 token 将超时 attempt 计为一次失败。 */
    int recoverTimedOut(Duration processingTimeout, int batchSize);
}

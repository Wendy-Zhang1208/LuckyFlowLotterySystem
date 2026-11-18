package com.luckyflow.lottery.service.Interface;

import java.time.Duration;

/** 批量恢复长期停留在 PROCESSING 状态的 Outbox 事件。 */
public interface OutboxRecoveryService {

    /**
     * 在同一短事务内锁定并恢复超时事件。
     *
     * @return 本批成功恢复的事件数量
     */
    int recoverTimedOut(Duration processingTimeout, int batchSize);
}

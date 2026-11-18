package com.luckyflow.lottery.service.Interface;

import com.luckyflow.lottery.model.LotterySuccessEvent;

/**
 * 将已经成立的领域事实写入 Transactional Outbox。
 */
public interface OutboxEventWriteService {

    /**
     * 在调用方现有数据库事务中持久化中奖事实。
     *
     * @param event 已成立的中奖事件
     */
    void writeLotterySuccessEvent(LotterySuccessEvent event);
}

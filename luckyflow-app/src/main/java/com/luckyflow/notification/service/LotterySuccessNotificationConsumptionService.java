package com.luckyflow.notification.service;

import com.luckyflow.lottery.model.LotterySuccessEvent;

/**
 * 将已经成立的中奖事实转换为可恢复的邮件、短信发送意图。
 */
public interface LotterySuccessNotificationConsumptionService {

    /**
     * 在一个 MySQL 本地事务内登记消费幂等记录并创建全部可用渠道的通知意图。
     */
    void consume(Long eventId, LotterySuccessEvent event);
}

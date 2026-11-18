package com.luckyflow.config.messaging.service;

import com.luckyflow.lottery.model.LotterySuccessEvent;

/**
 * 编排中奖事实的消费幂等与后续履约初始化。
 */
public interface LotterySuccessEventConsumptionService {

    /**
     * 消费一条中奖事实；相同消费者重复收到同一事件时正常返回。
     *
     * @param eventId Outbox 事件标识
     * @param event 已成立的中奖事实
     */
    void consume(Long eventId, LotterySuccessEvent event);
}

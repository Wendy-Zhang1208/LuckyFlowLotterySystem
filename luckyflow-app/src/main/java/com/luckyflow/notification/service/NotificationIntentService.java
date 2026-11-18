package com.luckyflow.notification.service;

import java.time.LocalDateTime;

/** 在中奖通知消费事务中创建单个渠道的持久化发送意图。 */
public interface NotificationIntentService {

    /**
     * 初始化单个渠道的通知意图；相同中奖记录与渠道的业务事实一致时按幂等成功处理。
     */
    void initialize(
            Long winningRecordId,
            Long userId,
            int channel,
            String recipient,
            LocalDateTime now
    );
}

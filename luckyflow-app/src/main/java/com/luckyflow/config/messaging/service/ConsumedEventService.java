package com.luckyflow.config.messaging.service;

import java.time.LocalDateTime;

/** 提供基于数据库唯一键的消息消费幂等登记能力。 */
public interface ConsumedEventService {

    /**
     * 尝试登记指定消费者对事件的本次消费。
     *
     * @return 首次登记返回 {@code true}，相同消费者已登记该事件时返回 {@code false}
     */
    boolean tryConsume(String consumerName, Long eventId, LocalDateTime consumeTime);
}

package com.luckyflow.config.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.luckyflow.lottery.model.LotterySuccessEvent;

/** 仅用于 Day12 基础链路验证；正式中奖消息必须由 Outbox Publisher 携带稳定 eventId 发布。 */
@Component
@ConditionalOnProperty(
        prefix = "luckyflow.rabbit.day12-test-topology",
        name = "enabled",
        havingValue = "true"
)
public class LotterySuccessEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public LotterySuccessEventProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(LotterySuccessEvent event) {
        rabbitTemplate.convertAndSend(
                LotteryEventRabbitTopology.EXCHANGE,
                LotteryEventRabbitTopology.SUCCESS_ROUTING_KEY,
                event
        );
    }
}

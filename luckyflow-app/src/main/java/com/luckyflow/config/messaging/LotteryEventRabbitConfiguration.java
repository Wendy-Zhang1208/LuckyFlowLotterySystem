package com.luckyflow.config.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 为 Day12 中奖事件基础链路声明 Exchange、测试 Queue、Binding 和 JSON 转换器。 */
@Configuration
public class LotteryEventRabbitConfiguration {

    @Bean
    public DirectExchange lotteryEventExchange() {
        return new DirectExchange(LotteryEventRabbitTopology.EXCHANGE);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "luckyflow.rabbit.day12-test-topology",
            name = "enabled",
            havingValue = "true"
    )
    public Queue lotterySuccessTestQueue() {
        return new Queue(LotteryEventRabbitTopology.SUCCESS_TEST_QUEUE);
    }

    /** 将测试队列通过固定中奖 routing key 绑定到抽奖事件交换机。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "luckyflow.rabbit.day12-test-topology",
            name = "enabled",
            havingValue = "true"
    )
    public Binding lotterySuccessTestBinding(
            @Qualifier("lotterySuccessTestQueue") Queue queue,
            @Qualifier("lotteryEventExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(LotteryEventRabbitTopology.SUCCESS_ROUTING_KEY);
    }

    /**
     * Spring AMQP 4 使用 Jackson 3 转换器，使 RabbitTemplate 与监听器共享同一 JSON 序列化协议。
     */
    @Bean
    public MessageConverter lotteryEventMessageConverter() {
        return new JacksonJsonMessageConverter("com.luckyflow.lottery.model");
    }
}

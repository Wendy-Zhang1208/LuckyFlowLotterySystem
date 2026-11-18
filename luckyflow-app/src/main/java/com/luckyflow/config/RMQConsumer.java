package com.luckyflow.config;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class RMQConsumer {
    @RabbitListener (queues = RMQConfig.DEMO_QUEUE)
    public void recieve(String message){
        System.out.println("收到 RabbitMQ 消息：" + message);
    }
}

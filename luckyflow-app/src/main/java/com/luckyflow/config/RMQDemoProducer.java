package com.luckyflow.config;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component 
// 演示消息只在显式 demo Profile 下发送，正常启动不得产生额外队列流量。
@Profile("demo")
public class RMQDemoProducer implements ApplicationRunner{

    private RabbitTemplate rabbitTemplate;

    public RMQDemoProducer(RabbitTemplate rabbitTemplate){
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        rabbitTemplate.convertAndSend(
            RMQConfig.DEMO_EXCHANGE,
            RMQConfig.DEMO_ROUTING_KEY,
            "Hello,Luke" );

            System.out.println("RabbitMQ 消息已发送");
    }

}

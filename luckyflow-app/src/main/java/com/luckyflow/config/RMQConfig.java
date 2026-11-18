package com.luckyflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration 
@Profile("demo")
public class RMQConfig {
    public static final String DEMO_EXCHANGE = "luckyflow.demo.exchange";
    public static final String DEMO_QUEUE = "luckyflow.demo.queue";
    public static final String DEMO_ROUTING_KEY = "demo.hello";

    @Bean 
    public DirectExchange demoExchange(){
        return new DirectExchange(DEMO_EXCHANGE);
    }

    @Bean
    public Queue demoQueue(){
        return new Queue(DEMO_QUEUE);
    }

    @Bean 
    public Binding demoBinding(
            DirectExchange demoExchange,
            Queue demoQueue){
            return BindingBuilder.bind(demoQueue)
            .to(demoExchange).with(DEMO_ROUTING_KEY);
    }

}

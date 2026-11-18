package com.luckyflow.config.messaging;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 声明独立的中奖通知 Quorum Queue、失败死信和手动确认监听容器。 */
@Configuration
@EnableConfigurationProperties(LotteryNotificationConsumerProperties.class)
@ConditionalOnProperty(
        prefix = "luckyflow.rabbit.notification-consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LotteryNotificationRabbitConfiguration {

    @Bean
    public DirectExchange lotteryNotificationDeadLetterExchange() {
        return new DirectExchange(
                LotteryEventRabbitTopology.NOTIFICATION_DEAD_LETTER_EXCHANGE
        );
    }

    /**
     * 通知队列与奖励履约队列使用相同中奖路由，但拥有独立 ACK、重投和死信生命周期。
     */
    @Bean
    public Queue lotteryNotificationQueue(
            LotteryNotificationConsumerProperties properties
    ) {
        properties.validate();
        return QueueBuilder.durable(LotteryEventRabbitTopology.NOTIFICATION_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .withArgument(
                        "x-delivery-limit",
                        properties.getInfrastructureDeliveryLimit()
                )
                .withArgument("x-delayed-retry-type", "failed")
                .withArgument(
                        "x-delayed-retry-min",
                        properties.getInfrastructureRetryMinMillis()
                )
                .withArgument(
                        "x-delayed-retry-max",
                        properties.getInfrastructureRetryMaxMillis()
                )
                .withArgument(
                        "x-dead-letter-exchange",
                        LotteryEventRabbitTopology.NOTIFICATION_DEAD_LETTER_EXCHANGE
                )
                .withArgument(
                        "x-dead-letter-routing-key",
                        LotteryEventRabbitTopology.NOTIFICATION_DEAD_LETTER_ROUTING_KEY
                )
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .build();
    }

    @Bean
    public Queue lotteryNotificationDeadLetterQueue() {
        return QueueBuilder.durable(
                        LotteryEventRabbitTopology.NOTIFICATION_DEAD_LETTER_QUEUE
                )
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    public Binding lotteryNotificationBinding(
            @Qualifier("lotteryNotificationQueue") Queue queue,
            @Qualifier("lotteryEventExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(LotteryEventRabbitTopology.SUCCESS_ROUTING_KEY);
    }

    @Bean
    public Binding lotteryNotificationDeadLetterBinding(
            @Qualifier("lotteryNotificationDeadLetterQueue") Queue queue,
            @Qualifier("lotteryNotificationDeadLetterExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(LotteryEventRabbitTopology.NOTIFICATION_DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * Consumer 在事务提交后手动 ACK，失败使用 basic.reject 累计 Broker 失败次数。
     */
    @Bean(name = "lotteryNotificationRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory
            lotteryNotificationRabbitListenerContainerFactory(
                    ConnectionFactory connectionFactory,
                    LotteryNotificationConsumerProperties properties
            ) {
        properties.validate();
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setMaxConcurrentConsumers(properties.getConcurrency());
        factory.setPrefetchCount(properties.getPrefetch());
        factory.setBatchSize(1);
        factory.setConsumerBatchEnabled(false);
        factory.setDefaultRequeueRejected(true);
        factory.setAutoStartup(properties.isAutoStartup());
        return factory;
    }
}

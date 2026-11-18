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

/** 正式奖励队列、两级延迟重试、DLQ 及专用监听容器配置。 */
@Configuration
@EnableConfigurationProperties(LotteryRewardConsumerProperties.class)
@ConditionalOnProperty(
        prefix = "luckyflow.rabbit.reward-consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LotteryRewardRabbitConfiguration {

    @Bean
    public DirectExchange lotteryRewardRetryExchange() {
        return new DirectExchange(LotteryEventRabbitTopology.REWARD_RETRY_EXCHANGE);
    }

    @Bean
    public DirectExchange lotteryRewardDeadLetterExchange() {
        return new DirectExchange(LotteryEventRabbitTopology.REWARD_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue lotteryRewardQueue(LotteryRewardConsumerProperties properties) {
        properties.validate();
        return QueueBuilder.durable(LotteryEventRabbitTopology.REWARD_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-delivery-limit", properties.getInfrastructureDeliveryLimit())
                .withArgument("x-delayed-retry-type", "failed")
                .withArgument("x-delayed-retry-min", properties.getInfrastructureRetryMinMillis())
                .withArgument("x-delayed-retry-max", properties.getInfrastructureRetryMaxMillis())
                .withArgument(
                        "x-dead-letter-exchange",
                        LotteryEventRabbitTopology.REWARD_DEAD_LETTER_EXCHANGE
                )
                .withArgument(
                        "x-dead-letter-routing-key",
                        LotteryEventRabbitTopology.REWARD_DEAD_LETTER_ROUTING_KEY
                )
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .build();
    }

    @Bean
    public Queue lotteryRewardRetryOneQueue(LotteryRewardConsumerProperties properties) {
        return retryQueue(
                LotteryEventRabbitTopology.REWARD_RETRY_1_QUEUE,
                properties.getRetryOneDelayMillis()
        );
    }

    @Bean
    public Queue lotteryRewardRetryTwoQueue(LotteryRewardConsumerProperties properties) {
        return retryQueue(
                LotteryEventRabbitTopology.REWARD_RETRY_2_QUEUE,
                properties.getRetryTwoDelayMillis()
        );
    }

    @Bean
    public Queue lotteryRewardDeadLetterQueue() {
        return QueueBuilder.durable(LotteryEventRabbitTopology.REWARD_DEAD_LETTER_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    public Binding lotteryRewardBinding(
            @Qualifier("lotteryRewardQueue") Queue queue,
            @Qualifier("lotteryEventExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(LotteryEventRabbitTopology.SUCCESS_ROUTING_KEY);
    }

    @Bean
    public Binding lotteryRewardReentryBinding(
            @Qualifier("lotteryRewardQueue") Queue queue,
            @Qualifier("lotteryRewardRetryExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(LotteryEventRabbitTopology.REWARD_REENTRY_ROUTING_KEY);
    }

    @Bean
    public Binding lotteryRewardRetryOneBinding(
            @Qualifier("lotteryRewardRetryOneQueue") Queue queue,
            @Qualifier("lotteryRewardRetryExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(LotteryEventRabbitTopology.REWARD_RETRY_1_ROUTING_KEY);
    }

    @Bean
    public Binding lotteryRewardRetryTwoBinding(
            @Qualifier("lotteryRewardRetryTwoQueue") Queue queue,
            @Qualifier("lotteryRewardRetryExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(LotteryEventRabbitTopology.REWARD_RETRY_2_ROUTING_KEY);
    }

    @Bean
    public Binding lotteryRewardDeadLetterBinding(
            @Qualifier("lotteryRewardDeadLetterQueue") Queue queue,
            @Qualifier("lotteryRewardDeadLetterExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(LotteryEventRabbitTopology.REWARD_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean(name = "lotteryRewardRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory lotteryRewardRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            LotteryRewardConsumerProperties properties
    ) {
        properties.validate();
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setMaxConcurrentConsumers(properties.getConcurrency());
        factory.setPrefetchCount(properties.getPrefetch());
        factory.setBatchSize(1);
        factory.setConsumerBatchEnabled(false);
        factory.setDefaultRequeueRejected(true);
        factory.setAutoStartup(properties.isAutoStartup());
        return factory;
    }

    private Queue retryQueue(String queueName, int delayMillis) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-message-ttl", delayMillis)
                .withArgument(
                        "x-dead-letter-exchange",
                        LotteryEventRabbitTopology.REWARD_RETRY_EXCHANGE
                )
                .withArgument(
                        "x-dead-letter-routing-key",
                        LotteryEventRabbitTopology.REWARD_REENTRY_ROUTING_KEY
                )
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .build();
    }
}

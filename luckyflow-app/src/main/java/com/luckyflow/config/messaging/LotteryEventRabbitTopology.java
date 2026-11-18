package com.luckyflow.config.messaging;

/**
 * 抽奖事件 RabbitMQ 拓扑名称。
 *
 * <p>Producer、Binding 和 Consumer 统一复用这些名称，避免拓扑调整时出现配置漂移。</p>
 */
public final class LotteryEventRabbitTopology {

    public static final String EXCHANGE = "luckyflow.lottery.event.exchange";
    public static final String SUCCESS_ROUTING_KEY = "lottery.success";
    public static final String SUCCESS_TEST_QUEUE = "luckyflow.lottery.success.test.queue";

    public static final String REWARD_QUEUE = "luckyflow.lottery.success.reward.queue";
    public static final String REWARD_RETRY_EXCHANGE =
            "luckyflow.lottery.success.reward.retry.exchange";
    public static final String REWARD_RETRY_1_QUEUE =
            "luckyflow.lottery.success.reward.retry.1.queue";
    public static final String REWARD_RETRY_2_QUEUE =
            "luckyflow.lottery.success.reward.retry.2.queue";
    public static final String REWARD_RETRY_1_ROUTING_KEY = "lottery.success.reward.retry.1";
    public static final String REWARD_RETRY_2_ROUTING_KEY = "lottery.success.reward.retry.2";
    public static final String REWARD_REENTRY_ROUTING_KEY = "lottery.success.reward.reentry";

    public static final String REWARD_DEAD_LETTER_EXCHANGE =
            "luckyflow.lottery.success.reward.dead.exchange";
    public static final String REWARD_DEAD_LETTER_QUEUE =
            "luckyflow.lottery.success.reward.dlq";
    public static final String REWARD_DEAD_LETTER_ROUTING_KEY =
            "lottery.success.reward.dead";

    /** 通知链路与奖励履约使用独立队列，任一消费者故障都不会阻塞另一条链路。 */
    public static final String NOTIFICATION_QUEUE =
            "luckyflow.lottery.success.notification.queue";
    public static final String NOTIFICATION_DEAD_LETTER_EXCHANGE =
            "luckyflow.lottery.success.notification.dead.exchange";
    public static final String NOTIFICATION_DEAD_LETTER_QUEUE =
            "luckyflow.lottery.success.notification.dlq";
    public static final String NOTIFICATION_DEAD_LETTER_ROUTING_KEY =
            "lottery.success.notification.dead";

    public static final String EVENT_ID_HEADER = "eventId";
    public static final String EVENT_TYPE_HEADER = "eventType";
    public static final String RETRY_COUNT_HEADER = "retryCount";
    public static final String FAILURE_TYPE_HEADER = "failureType";
    public static final String FAILURE_MESSAGE_HEADER = "failureMessage";

    private LotteryEventRabbitTopology() {
    }
}

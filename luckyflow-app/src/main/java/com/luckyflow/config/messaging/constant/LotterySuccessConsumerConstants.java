package com.luckyflow.config.messaging.constant;

/**
 * 中奖事实在各下游业务中的稳定消费者标识。
 */
public final class LotterySuccessConsumerConstants {

    /**
     * 已持久化的奖励消费者幂等作用域；沿用原值可避免部署升级后把历史实物事件当作新事件消费。
     */
    public static final String FULFILLMENT_CONSUMER_NAME = "lottery-success-fulfillment";

    public static final String REWARD_CONSUMER_NAME = FULFILLMENT_CONSUMER_NAME;

    /** 通知意图拥有独立幂等作用域，不能被奖励履约消费者的消费记录抑制。 */
    public static final String NOTIFICATION_CONSUMER_NAME = "lottery-success-notification";

    private LotterySuccessConsumerConstants() {
    }
}

package com.luckyflow.user.constant;

/**
 * 积分流水在数据库中存储的业务来源值。
 */
public final class PointTransactionBizTypeConstants {

    public static final String POINT_TRANSACTION_BIZ_TYPE_DRAW = "DRAW";

    /** 抽奖中奖后发放积分奖励，一条中奖记录最多对应一条该类型流水。 */
    public static final String POINT_TRANSACTION_BIZ_TYPE_LOTTERY_REWARD = "LOTTERY_REWARD";

    /**
     * 工具类，不允许实例化。
     */
    private PointTransactionBizTypeConstants() {
    }
}

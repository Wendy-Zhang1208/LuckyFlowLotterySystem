package com.luckyflow.lottery.constant;

/** 抽奖所需 Redis 资源预占意图的状态。 */
public final class DrawResourceReservationStatusConstants {

    /** 预占意图已落库，但 Redis 预占结果尚未确定。 */
    public static final int PREPARED = 0;

    /** Redis 资源已经预占，业务失败时必须补偿。 */
    public static final int RESERVED = 1;

    /** Redis 明确返回未预占成功，无需补偿。 */
    public static final int NOT_RESERVED = 2;

    /** 抽奖业务已经提交，预占资源转为正式消耗。 */
    public static final int CONFIRMED = 3;

    /** 抽奖未成功，预占资源已经完成幂等补偿。 */
    public static final int COMPENSATED = 4;

    private DrawResourceReservationStatusConstants() {
    }
}

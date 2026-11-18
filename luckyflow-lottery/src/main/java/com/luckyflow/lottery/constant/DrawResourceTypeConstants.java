package com.luckyflow.lottery.constant;

/** 抽奖流程中需要持久化预占意图的 Redis 资源类型。 */
public final class DrawResourceTypeConstants {

    public static final int DAILY_DRAW_LIMIT = 1;

    public static final int PRIZE_INVENTORY = 2;

    private DrawResourceTypeConstants() {
    }
}

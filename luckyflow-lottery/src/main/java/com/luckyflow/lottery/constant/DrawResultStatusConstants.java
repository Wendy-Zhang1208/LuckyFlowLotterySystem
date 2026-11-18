package com.luckyflow.lottery.constant;

/**
 * 抽奖记录在数据库中存储的结果状态值。
 */
public final class DrawResultStatusConstants {

    public static final int DRAW_RESULT_NO_WIN = 0;
    public static final int DRAW_RESULT_WIN = 1;

    /**
     * 工具类，不允许实例化。
     */
    private DrawResultStatusConstants() {
    }
}
